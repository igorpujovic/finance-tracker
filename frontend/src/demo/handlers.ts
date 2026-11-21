import { HttpResponse, http } from 'msw';
import type {
  CategoryTotal,
  ComparisonRow,
  Transaction,
  TransactionType,
} from '../api/types';
import {
  categorize,
  importStatement,
  seed,
  toDinars,
  typeOf,
  type Db,
} from './store';

/**
 * The API, served from the browser.
 *
 * Every route the client calls is here, answering the shapes in `api/types.ts`
 * — including the failures, because a demo where nothing can go wrong hides
 * half of what this client does. Deleting a category that still holds money is
 * a real 409 here, and an unpriced currency is a real 400 with the server's own
 * wording.
 *
 * The one thing deliberately not reproduced is the 503 from an unreachable
 * National Bank: there is no network call to fail, and faking an outage on a
 * timer would be a demo that breaks itself while somebody is looking at it.
 */

const db: Db = seed();

/**
 * Scoped to the page's own base path. On GitHub Pages the app is served from
 * `/finance-tracker/`, and a service worker cannot intercept requests outside
 * its own scope — so the client asks for `/finance-tracker/api/...` there and
 * `/api/...` when served from the jar, and these patterns follow it.
 */
const base = `${import.meta.env.BASE_URL.replace(/\/$/, '')}/api`;
const path = (suffix: string) => `${base}${suffix}`;

const json = HttpResponse.json;

function error(status: number, message: string, fieldErrors?: Record<string, string>) {
  return json(
    {
      timestamp: new Date().toISOString(),
      status,
      error:
        status === 404
          ? 'Not Found'
          : status === 409
            ? 'Conflict'
            : status === 503
              ? 'Service Unavailable'
              : 'Bad Request',
      message,
      ...(fieldErrors ? { fieldErrors } : {}),
    },
    { status },
  );
}

const byDateDesc = (a: Transaction, b: Transaction) =>
  b.date.localeCompare(a.date) || b.id - a.id;

/** A month's transactions, by the first ten characters of the ISO date. */
function inMonth(year: number, month: number): Transaction[] {
  const prefix = `${year}-${String(month).padStart(2, '0')}`;
  return db.transactions.filter((t) => t.date.startsWith(prefix));
}

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

/**
 * Category totals for a month, uncategorized included as a null-category row.
 * That row is what makes the totals add up to the balance, so it is produced
 * here rather than filtered out.
 */
function totalsFor(transactions: Transaction[]): CategoryTotal[] {
  const groups = new Map<string, CategoryTotal>();
  for (const t of transactions) {
    const key = String(t.categoryId ?? 'uncategorized');
    const existing = groups.get(key);
    if (existing) {
      existing.totalRsd = round2(existing.totalRsd + t.amountRsd);
      continue;
    }
    const category = db.categories.find((c) => c.id === t.categoryId);
    groups.set(key, {
      categoryId: t.categoryId,
      categoryName: t.categoryName,
      categoryType: category?.type ?? null,
      totalRsd: round2(t.amountRsd),
    });
  }
  return [...groups.values()].sort((a, b) => a.totalRsd - b.totalRsd);
}

export const handlers = [
  // --- categories --------------------------------------------------------

  http.get(path('/categories'), () =>
    json([...db.categories].sort((a, b) => a.name.localeCompare(b.name))),
  ),

  http.post(path('/categories'), async ({ request }) => {
    const body = (await request.json()) as { name: string; type: 'INCOME' | 'EXPENSE' };
    if (!body.name?.trim()) return error(400, 'Validation failed', { name: 'must not be blank' });
    if (db.categories.some((c) => c.name.toLowerCase() === body.name.trim().toLowerCase())) {
      return error(409, `a category named ${body.name} already exists`);
    }
    const created = {
      id: Math.max(0, ...db.categories.map((c) => c.id)) + 1,
      name: body.name.trim(),
      type: body.type,
    };
    db.categories.push(created);
    return json(created, { status: 201 });
  }),

  http.put(path('/categories/:id'), async ({ params, request }) => {
    const id = Number(params['id']);
    const category = db.categories.find((c) => c.id === id);
    if (!category) return error(404, `no category with id ${id}`);
    const body = (await request.json()) as { name: string; type: 'INCOME' | 'EXPENSE' };
    const holders = db.transactions.filter((t) => t.categoryId === id);
    if (body.type !== category.type && holders.some((t) => t.type !== body.type)) {
      return error(409, `${category.name} holds transactions of the other sign`);
    }
    category.name = body.name.trim();
    category.type = body.type;
    for (const t of holders) t.categoryName = category.name;
    for (const rule of db.rules.filter((r) => r.categoryId === id)) {
      rule.categoryName = category.name;
      rule.categoryType = category.type;
    }
    return json(category);
  }),

  http.delete(path('/categories/:id'), ({ params }) => {
    const id = Number(params['id']);
    const category = db.categories.find((c) => c.id === id);
    if (!category) return error(404, `no category with id ${id}`);
    const held = db.transactions.filter((t) => t.categoryId === id).length;
    if (held > 0) {
      // The conflict the UI is built to show, and the reason the delete button
      // is not hidden: the count is the server's to know.
      return error(409, `${category.name} still holds ${held} transaction${held === 1 ? '' : 's'}`);
    }
    db.categories = db.categories.filter((c) => c.id !== id);
    db.rules = db.rules.filter((r) => r.categoryId !== id);
    return new HttpResponse(null, { status: 204 });
  }),

  // --- transactions ------------------------------------------------------

  http.get(path('/transactions'), ({ request }) => {
    const url = new URL(request.url);
    const q = url.searchParams;
    let rows = [...db.transactions].sort(byDateDesc);

    const from = q.get('from');
    const to = q.get('to');
    const categoryId = q.get('categoryId');
    const type = q.get('type');

    if (from) rows = rows.filter((t) => t.date >= from);
    if (to) rows = rows.filter((t) => t.date <= to);
    if (categoryId) rows = rows.filter((t) => t.categoryId === Number(categoryId));
    if (type) rows = rows.filter((t) => t.type === type);
    if (q.get('uncategorized') === 'true') rows = rows.filter((t) => t.categoryId === null);

    const page = Number(q.get('page') ?? 0);
    const size = Number(q.get('size') ?? 20);
    const start = page * size;

    return json({
      content: rows.slice(start, start + size),
      page,
      size,
      totalElements: rows.length,
      totalPages: Math.max(1, Math.ceil(rows.length / size)),
    });
  }),

  http.get(path('/transactions/:id'), ({ params }) => {
    const transaction = db.transactions.find((t) => t.id === Number(params['id']));
    return transaction ? json(transaction) : error(404, `no transaction with id ${params['id']}`);
  }),

  http.post(path('/transactions'), async ({ request }) => {
    const body = (await request.json()) as {
      date: string;
      description: string;
      amount: number;
      currency: string;
      categoryId: number | null;
    };
    const problem = validate(body);
    if (problem) return problem;

    const currency = body.currency.toUpperCase();
    const amountRsd = toDinars(body.amount, currency);
    if (amountRsd === null) return error(400, `no exchange rate for ${currency} on ${body.date}`);

    const category = db.categories.find((c) => c.id === body.categoryId) ?? null;
    const created: Transaction = {
      id: db.nextId++,
      date: body.date,
      description: body.description.trim(),
      amount: body.amount,
      currency,
      amountRsd,
      type: typeOf(body.amount),
      categoryId: category?.id ?? null,
      categoryName: category?.name ?? null,
      source: 'MANUAL',
      importBatchId: null,
      createdAt: new Date().toISOString(),
    };
    db.transactions.push(created);
    return json(created, { status: 201 });
  }),

  http.put(path('/transactions/:id'), async ({ params, request }) => {
    const id = Number(params['id']);
    const transaction = db.transactions.find((t) => t.id === id);
    // Asked before the currency is, so a PUT to a missing id is a 404 about the
    // row rather than a 400 about the currency of something that is not there.
    if (!transaction) return error(404, `no transaction with id ${id}`);

    const body = (await request.json()) as {
      date: string;
      description: string;
      amount: number;
      currency: string;
      categoryId: number | null;
    };
    const problem = validate(body);
    if (problem) return problem;

    const currency = body.currency.toUpperCase();
    const amountRsd = toDinars(body.amount, currency);
    if (amountRsd === null) return error(400, `no exchange rate for ${currency} on ${body.date}`);

    const category = db.categories.find((c) => c.id === body.categoryId) ?? null;
    Object.assign(transaction, {
      date: body.date,
      description: body.description.trim(),
      amount: body.amount,
      currency,
      amountRsd,
      type: typeOf(body.amount),
      categoryId: category?.id ?? null,
      categoryName: category?.name ?? null,
    });
    return json(transaction);
  }),

  http.patch(path('/transactions/:id/category'), async ({ params, request }) => {
    const id = Number(params['id']);
    const transaction = db.transactions.find((t) => t.id === id);
    if (!transaction) return error(404, `no transaction with id ${id}`);

    const { categoryId } = (await request.json()) as { categoryId: number | null };
    if (categoryId === null) {
      transaction.categoryId = null;
      transaction.categoryName = null;
      return json(transaction);
    }
    const category = db.categories.find((c) => c.id === categoryId);
    if (!category) return error(404, `no category with id ${categoryId}`);
    if (!canTake(transaction.type, category.type)) {
      return error(409, `${category.name} is an ${category.type.toLowerCase()} category`);
    }
    transaction.categoryId = category.id;
    transaction.categoryName = category.name;
    return json(transaction);
  }),

  http.delete(path('/transactions/:id'), ({ params }) => {
    const id = Number(params['id']);
    if (!db.transactions.some((t) => t.id === id)) return error(404, `no transaction with id ${id}`);
    db.transactions = db.transactions.filter((t) => t.id !== id);
    return new HttpResponse(null, { status: 204 });
  }),

  // --- rules -------------------------------------------------------------

  http.get(path('/rules'), () =>
    json([...db.rules].sort((a, b) => b.priority - a.priority || a.keyword.localeCompare(b.keyword))),
  ),

  http.post(path('/rules'), async ({ request }) => {
    const body = (await request.json()) as {
      keyword: string;
      categoryId: number;
      priority?: number;
    };
    if (!body.keyword?.trim()) {
      return error(400, 'Validation failed', { keyword: 'must not be blank' });
    }
    const category = db.categories.find((c) => c.id === body.categoryId);
    if (!category) return error(400, 'Validation failed', { categoryId: 'must exist' });

    const created = {
      id: Math.max(0, ...db.rules.map((r) => r.id)) + 1,
      keyword: body.keyword.trim(),
      categoryId: category.id,
      categoryName: category.name,
      categoryType: category.type,
      priority: body.priority ?? 0,
    };
    db.rules.push(created);
    return json(created, { status: 201 });
  }),

  http.put(path('/rules/:id'), async ({ params, request }) => {
    const rule = db.rules.find((r) => r.id === Number(params['id']));
    if (!rule) return error(404, `no rule with id ${params['id']}`);
    const body = (await request.json()) as {
      keyword: string;
      categoryId: number;
      priority?: number;
    };
    const category = db.categories.find((c) => c.id === body.categoryId);
    if (!category) return error(400, 'Validation failed', { categoryId: 'must exist' });
    Object.assign(rule, {
      keyword: body.keyword.trim(),
      categoryId: category.id,
      categoryName: category.name,
      categoryType: category.type,
      priority: body.priority ?? 0,
    });
    return json(rule);
  }),

  http.delete(path('/rules/:id'), ({ params }) => {
    const id = Number(params['id']);
    if (!db.rules.some((r) => r.id === id)) return error(404, `no rule with id ${id}`);
    db.rules = db.rules.filter((r) => r.id !== id);
    return new HttpResponse(null, { status: 204 });
  }),

  http.post(path('/rules/apply'), () => {
    const uncategorized = db.transactions.filter((t) => t.categoryId === null);
    let categorized = 0;
    for (const transaction of uncategorized) {
      const category = categorize(transaction.description, transaction.type, db);
      if (!category) continue;
      transaction.categoryId = category.id;
      transaction.categoryName = category.name;
      categorized++;
    }
    return json({ examined: uncategorized.length, categorized });
  }),

  // --- imports -----------------------------------------------------------

  http.post(path('/imports'), async ({ request }) => {
    const form = await request.formData();
    const file = form.get('file');
    if (!(file instanceof File)) return error(400, 'the request carried no file');
    const text = await file.text();
    if (!text.trim()) return error(400, 'the statement is empty');
    const { batch } = importStatement(db, file.name, text);
    return json(batch, { status: 201 });
  }),

  http.get(path('/imports'), ({ request }) => {
    const page = Number(new URL(request.url).searchParams.get('page') ?? 0);
    const size = 20;
    const rows = [...db.batches].sort((a, b) => b.id - a.id);
    const start = page * size;
    return json({
      // The list omits `errors` — an absent field says "not loaded" where an
      // empty array would claim the import rejected nothing.
      content: rows.slice(start, start + size).map(({ errors: _errors, ...rest }) => rest),
      page,
      size,
      totalElements: rows.length,
      totalPages: Math.max(1, Math.ceil(rows.length / size)),
    });
  }),

  http.get(path('/imports/:id'), ({ params }) => {
    const batch = db.batches.find((b) => b.id === Number(params['id']));
    return batch
      ? json({ ...batch, errors: batch.errors ?? [] })
      : error(404, `no import with id ${params['id']}`);
  }),

  // --- reports -----------------------------------------------------------

  http.get(path('/reports/monthly'), ({ request }) => {
    const q = new URL(request.url).searchParams;
    const year = Number(q.get('year'));
    const month = Number(q.get('month'));
    const rows = inMonth(year, month);
    const income = round2(
      rows.filter((t) => t.amountRsd > 0).reduce((sum, t) => sum + t.amountRsd, 0),
    );
    const expenses = round2(
      rows.filter((t) => t.amountRsd < 0).reduce((sum, t) => sum + t.amountRsd, 0),
    );
    return json({
      year,
      month,
      income,
      expenses,
      balance: round2(income + expenses),
      categories: totalsFor(rows),
    });
  }),

  http.get(path('/reports/comparison'), ({ request }) => {
    const q = new URL(request.url).searchParams;
    const year = Number(q.get('year'));
    const month = Number(q.get('month'));
    const previousMonth = month === 1 ? 12 : month - 1;
    const previousYear = month === 1 ? year - 1 : year;

    const current = totalsFor(inMonth(year, month));
    const previous = totalsFor(inMonth(previousYear, previousMonth));

    // A category active in either month appears, so one that stopped is as
    // visible as one that started.
    const keys = new Set([
      ...current.map((c) => String(c.categoryId ?? 'uncategorized')),
      ...previous.map((c) => String(c.categoryId ?? 'uncategorized')),
    ]);

    const categories: ComparisonRow[] = [...keys].map((key) => {
      const now = current.find((c) => String(c.categoryId ?? 'uncategorized') === key);
      const before = previous.find((c) => String(c.categoryId ?? 'uncategorized') === key);
      const source = now ?? before;
      const currentTotal = now?.totalRsd ?? 0;
      const previousTotal = before?.totalRsd ?? 0;
      const change = round2(currentTotal - previousTotal);
      return {
        categoryId: source?.categoryId ?? null,
        categoryName: source?.categoryName ?? null,
        categoryType: source?.categoryType ?? null,
        current: currentTotal,
        previous: previousTotal,
        change,
        // Null when the previous month was zero: no percentage describes it.
        percent: previousTotal === 0 ? null : round2((change / Math.abs(previousTotal)) * 100),
      };
    });

    return json({ year, month, previousYear, previousMonth, categories });
  }),

  http.get(path('/reports/top-expenses'), ({ request }) => {
    const q = new URL(request.url).searchParams;
    const from = q.get('from') ?? '';
    const to = q.get('to') ?? '';
    const limit = Number(q.get('limit') ?? 10);
    const expenses = db.transactions
      .filter((t) => t.amountRsd < 0 && t.date >= from && t.date <= to)
      .sort((a, b) => a.amountRsd - b.amountRsd)
      .slice(0, limit)
      .map(({ id, date, description, amount, currency, amountRsd, categoryId, categoryName }) => ({
        id,
        date,
        description,
        amount,
        currency,
        amountRsd,
        categoryId,
        categoryName,
      }));
    return json({ from, to, limit, expenses });
  }),

  http.get(path('/reports/monthly/export'), ({ request }) => {
    const q = new URL(request.url).searchParams;
    const year = Number(q.get('year'));
    const month = Number(q.get('month'));
    const rows = totalsFor(inMonth(year, month));
    const body =
      'category,type,total_rsd\n' +
      rows
        .map(
          (row) =>
            `${row.categoryName ?? 'Uncategorized'},${row.categoryType ?? ''},${row.totalRsd.toFixed(2)}`,
        )
        .join('\n') +
      '\n';
    // The byte-order mark is part of the file's contract: it is what makes
    // Serbian category names survive a double-click into Excel.
    return new HttpResponse(`﻿${body}`, {
      headers: {
        'Content-Type': 'text/csv;charset=UTF-8',
        'Content-Disposition': `attachment; filename="finance-${year}-${String(month).padStart(2, '0')}.csv"`,
      },
    });
  }),

  // --- exchange rates ----------------------------------------------------

  http.post(path('/exchange-rates/refresh'), () =>
    json({ asOf: new Date().toISOString().slice(0, 10), published: 3, stored: 0 }),
  ),
];

function canTake(transaction: TransactionType, category: 'INCOME' | 'EXPENSE'): boolean {
  return transaction === category;
}

function validate(body: {
  date: string;
  description: string;
  amount: number;
  currency: string;
}) {
  const fieldErrors: Record<string, string> = {};
  if (!body.date) fieldErrors['date'] = 'must not be null';
  if (!body.description?.trim()) fieldErrors['description'] = 'must not be blank';
  // ADR-0002: zero has no sign and therefore no type.
  if (!body.amount) fieldErrors['amount'] = 'must not be zero';
  if (!/^[A-Za-z]{3}$/.test(body.currency ?? '')) {
    fieldErrors['currency'] = 'must be a 3-letter ISO currency code';
  }
  return Object.keys(fieldErrors).length > 0
    ? error(400, 'Validation failed', fieldErrors)
    : null;
}
