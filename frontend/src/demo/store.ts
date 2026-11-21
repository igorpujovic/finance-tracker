/**
 * The demo's in-memory stand-in for the server.
 *
 * This exists so the published demo is the *real client* — the same components,
 * the same hooks, the same error handling — with only the network replaced.
 * A screenshot gallery would have been less work and would prove less.
 *
 * It is a reimplementation and therefore a liability: two copies of a rule can
 * disagree, and the Java one is the truth. It is kept honest in two ways. The
 * seeded categories and rules below are copied from `V2__seed_categories.sql`
 * and `V3__seed_rules.sql`, and the figures the demo opens on are the ones the
 * README pins and the test suite asserts — so if this file drifts, the demo
 * stops matching the documentation and the drift is visible rather than
 * subtle. Nothing here ships inside the application jar; it is compiled only
 * into the GitHub Pages build.
 */

import type {
  Category,
  CategoryType,
  ImportBatch,
  ImportError,
  Rule,
  Transaction,
  TransactionType,
} from '../api/types';

export interface Db {
  categories: Category[];
  rules: Rule[];
  transactions: Transaction[];
  batches: ImportBatch[];
  /** Fingerprints of every imported source line, as ADR-0003 describes. */
  fingerprints: Set<string>;
  nextId: number;
}

/**
 * Rates for the two dates the seeded data needs, plus a fallback. The real
 * client reads these from the National Bank at import time; a static demo has
 * no server to ask, so a foreign line converts at the rate recorded here —
 * 117.2061 being the rate the README's Netflix line actually converted at.
 */
const RATES: Record<string, number> = { EUR: 117.2061, USD: 108.4, CHF: 124.9 };

export const UNKNOWN_CURRENCY = 'ZWL';

function round2(value: number): number {
  return Math.round(value * 100) / 100;
}

/** The dinar value of an amount, at this demo's fixed rates. */
export function toDinars(amount: number, currency: string): number | null {
  if (currency === 'RSD') return round2(amount);
  const rate = RATES[currency];
  if (rate === undefined) return null;
  return round2(amount * rate);
}

export function typeOf(amount: number): TransactionType {
  return amount > 0 ? 'INCOME' : 'EXPENSE';
}

/** ADR-0003: a source line is recognised by these four things and nothing else. */
export function fingerprint(
  date: string,
  description: string,
  amount: number,
  currency: string,
): string {
  return `${date}|${description.trim().toUpperCase()}|${amount.toFixed(2)}|${currency}`;
}

/**
 * ADR-0004, in the order the ADR gives: the category's type has to be able to
 * take the transaction at all, and only then does priority rank what is left.
 * This is why a refund carrying a merchant's name stays uncategorized — the
 * MAXI rule matches it and is then discarded, because an expense category
 * cannot take income.
 */
export function categorize(
  description: string,
  type: TransactionType,
  db: Db,
): Category | null {
  const text = description.toUpperCase();
  const candidates = db.rules
    .filter((rule) => text.includes(rule.keyword.toUpperCase()))
    .filter((rule) =>
      type === 'INCOME' ? rule.categoryType === 'INCOME' : rule.categoryType === 'EXPENSE',
    )
    .sort((a, b) => b.priority - a.priority);

  const winner = candidates[0];
  if (!winner) return null;
  return db.categories.find((category) => category.id === winner.categoryId) ?? null;
}

function category(db: Db, name: string): Category {
  const found = db.categories.find((c) => c.name === name);
  if (!found) throw new Error(`the demo seed is missing the ${name} category`);
  return found;
}

/** Builds a transaction and files it by the rules, the way an import would. */
function imported(
  db: Db,
  date: string,
  description: string,
  amount: number,
  currency: string,
  batchId: number,
): Transaction {
  const amountRsd = toDinars(amount, currency);
  if (amountRsd === null) throw new Error(`the demo seed uses an unpriced currency: ${currency}`);
  const type = typeOf(amount);
  const assigned = categorize(description, type, db);
  db.fingerprints.add(fingerprint(date, description, amount, currency));
  return {
    id: db.nextId++,
    date,
    description,
    amount,
    currency,
    amountRsd,
    type,
    categoryId: assigned?.id ?? null,
    categoryName: assigned?.name ?? null,
    source: 'IMPORT',
    importBatchId: batchId,
    createdAt: new Date(`${date}T09:00:00Z`).toISOString(),
  };
}

function manual(
  db: Db,
  date: string,
  description: string,
  amount: number,
  categoryName: string | null,
): Transaction {
  const assigned = categoryName === null ? null : category(db, categoryName);
  return {
    id: db.nextId++,
    date,
    description,
    amount,
    currency: 'RSD',
    amountRsd: round2(amount),
    type: typeOf(amount),
    categoryId: assigned?.id ?? null,
    categoryName: assigned?.name ?? null,
    source: 'MANUAL',
    importBatchId: null,
    createdAt: new Date(`${date}T09:00:00Z`).toISOString(),
  };
}

/**
 * The state the demo opens on.
 *
 * November is `sample.csv` exactly — the file in this repository, including the
 * duplicate and the two unusable lines — so the dashboard shows the figures the
 * README pins: 95.000,00 income, −8.495,64 expenses, 86.504,36 balance, with
 * the euro line at −1.174,84.
 *
 * October is added so the comparison report has something to compare against,
 * and is chosen to produce every row the report can produce: a category that
 * shrank (Food), two that stopped (Transport, Health), two with no previous
 * month at all (Utilities, Entertainment), one unchanged (Salary), and the
 * refund that no rule can claim.
 */
export function seed(): Db {
  const db: Db = {
    categories: [],
    rules: [],
    transactions: [],
    batches: [],
    fingerprints: new Set(),
    nextId: 1,
  };

  // V2__seed_categories.sql
  const categories: [string, CategoryType][] = [
    ['Salary', 'INCOME'],
    ['Refunds', 'INCOME'],
    ['Food', 'EXPENSE'],
    ['Utilities', 'EXPENSE'],
    ['Transport', 'EXPENSE'],
    ['Entertainment', 'EXPENSE'],
    ['Health', 'EXPENSE'],
  ];
  db.categories = categories.map(([name, type], index) => ({ id: index + 1, name, type }));

  // V3__seed_rules.sql
  const rules: [string, string, number][] = [
    ['PLATA', 'Salary', 100],
    ['POVRACAJ', 'Refunds', 100],
    ['MAXI', 'Food', 50],
    ['LIDL', 'Food', 50],
    ['EPS', 'Utilities', 50],
    ['BUS PLUS', 'Transport', 50],
    ['NETFLIX', 'Entertainment', 50],
  ];
  db.rules = rules.map(([keyword, categoryName, priority], index) => {
    const target = category(db, categoryName);
    return {
      id: index + 1,
      keyword,
      categoryId: target.id,
      categoryName: target.name,
      categoryType: target.type,
      priority,
    };
  });

  db.nextId = 1;

  // October, entered by hand.
  db.transactions.push(
    manual(db, '2026-10-01', 'PLATA SEPTEMBAR', 95000.0, 'Salary'),
    manual(db, '2026-10-03', 'MAXI 245 BEOGRAD', -5000.0, 'Food'),
    manual(db, '2026-10-15', 'BUS PLUS DOPUNA', -1500.0, 'Transport'),
    manual(db, '2026-10-20', 'APOTEKA', -800.0, 'Health'),
  );

  // November, from sample.csv.
  const batchId = 1;
  db.transactions.push(
    imported(db, '2026-11-01', 'PLATA OKTOBAR', 95000.0, 'RSD', batchId),
    imported(db, '2026-11-03', 'MAXI 245 BEOGRAD', -2450.8, 'RSD', batchId),
    imported(db, '2026-11-04', 'NETFLIX.COM, AMSTERDAM', -9.99, 'EUR', batchId),
    imported(db, '2026-11-05', 'EPS SNABDEVANJE', -4870.0, 'RSD', batchId),
  );

  // The refund the POVRACAJ rule would catch, worded so it does not — which is
  // the documented limitation, and the reason the uncategorized row has money.
  db.transactions.push(manual(db, '2026-11-18', 'MAXI 245 BEOGRAD', 350.0, null));

  db.batches.push({
    id: batchId,
    fileName: 'sample.csv',
    importedAt: '2026-11-21T13:21:49.665369Z',
    totalRows: 7,
    importedRows: 4,
    duplicateRows: 1,
    rejectedRows: 2,
    errors: [
      {
        rowNumber: 7,
        rawLine: '31.11.2026,BUS PLUS DOPUNA,-1500.00,RSD',
        reason: 'the date is not a real date written as dd.MM.yyyy',
      },
      {
        rowNumber: 8,
        rawLine: '07.11.2026,LIDL 112,abc,RSD',
        reason: 'the amount is not a number',
      },
    ],
  });

  return db;
}

// --- importing a statement ----------------------------------------------

const DATE = /^(\d{2})\.(\d{2})\.(\d{4})$/;

/** `dd.MM.yyyy` to an ISO date, or null when it is not a real day. */
export function parseDate(text: string): string | null {
  const match = DATE.exec(text.trim());
  if (!match) return null;
  const [, dd, mm, yyyy] = match;
  const day = Number(dd);
  const month = Number(mm);
  const year = Number(yyyy);
  if (month < 1 || month > 12) return null;
  // The 31st of November is not a date, and this is where the sample statement
  // proves it: a rejected line never stops the rest of the file importing.
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  if (day < 1 || day > lastDay) return null;
  return `${yyyy}-${mm}-${dd}`;
}

/** Splits one CSV line, honouring the quotes a description with a comma needs. */
export function splitCsvLine(line: string): string[] {
  const fields: string[] = [];
  let current = '';
  let quoted = false;
  for (let i = 0; i < line.length; i++) {
    const char = line[i];
    if (quoted) {
      if (char === '"' && line[i + 1] === '"') {
        current += '"';
        i++;
      } else if (char === '"') {
        quoted = false;
      } else {
        current += char;
      }
    } else if (char === '"') {
      quoted = true;
    } else if (char === ',') {
      fields.push(current);
      current = '';
    } else {
      current += char;
    }
  }
  fields.push(current);
  return fields;
}

export interface ImportOutcome {
  batch: ImportBatch;
  created: Transaction[];
}

/**
 * Imports a statement into the demo store, following the rules the real
 * importer follows: a rejected line never stops the import, a duplicate is
 * counted and skipped rather than rejected, and duplicates are detected within
 * one file as well as against what is already stored.
 */
export function importStatement(db: Db, fileName: string, text: string): ImportOutcome {
  const lines = text.split(/\r?\n/).filter((line) => line.trim() !== '');
  const body = lines.slice(1); // the header is line 1
  const errors: ImportError[] = [];
  const created: Transaction[] = [];
  const batchId = (db.batches.at(-1)?.id ?? 0) + 1;
  let duplicates = 0;

  body.forEach((line, index) => {
    const rowNumber = index + 2;
    const reject = (reason: string) => errors.push({ rowNumber, rawLine: line, reason });

    const fields = splitCsvLine(line);
    if (fields.length < 4) return reject('the line does not have four columns');

    const [rawDate, description, rawAmount, rawCurrency] = fields as [
      string,
      string,
      string,
      string,
    ];

    const date = parseDate(rawDate);
    if (date === null) return reject('the date is not a real date written as dd.MM.yyyy');

    const amount = Number(rawAmount.trim());
    if (rawAmount.trim() === '' || Number.isNaN(amount)) {
      return reject('the amount is not a number');
    }
    if (amount === 0) return reject('the amount is zero, which has no type');

    const currency = rawCurrency.trim().toUpperCase();
    const amountRsd = toDinars(amount, currency);
    if (amountRsd === null) return reject(`no exchange rate for ${currency} on ${date}`);

    const print = fingerprint(date, description, amount, currency);
    if (db.fingerprints.has(print)) {
      duplicates++;
      return;
    }
    db.fingerprints.add(print);

    const type = typeOf(amount);
    const assigned = categorize(description, type, db);
    const transaction: Transaction = {
      id: db.nextId++,
      date,
      description,
      amount,
      currency,
      amountRsd,
      type,
      categoryId: assigned?.id ?? null,
      categoryName: assigned?.name ?? null,
      source: 'IMPORT',
      importBatchId: batchId,
      createdAt: new Date().toISOString(),
    };
    created.push(transaction);
  });

  db.transactions.push(...created);

  const batch: ImportBatch = {
    id: batchId,
    fileName,
    importedAt: new Date().toISOString(),
    totalRows: body.length,
    importedRows: created.length,
    duplicateRows: duplicates,
    rejectedRows: errors.length,
    errors,
  };
  db.batches.push(batch);
  return { batch, created };
}
