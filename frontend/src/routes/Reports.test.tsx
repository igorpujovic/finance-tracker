import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { Reports } from './Reports';

/**
 * The comparison table, rendered from a real API payload.
 *
 * This is the report a reader is most likely to misread, and the rows below are
 * the four shapes it can produce — a category that shrank, one that stopped,
 * one that is new, and the uncategorized row. Each is asserted on the rendered
 * table rather than on a helper, because the bug these guard against is the
 * component quietly dropping or flipping something on its way to the screen.
 */

const COMPARISON = {
  year: 2026,
  month: 11,
  previousYear: 2026,
  previousMonth: 10,
  categories: [
    // Spending halved: the project's own worked example.
    {
      categoryId: 3,
      categoryName: 'Food',
      categoryType: 'EXPENSE',
      current: -2450.8,
      previous: -5000.0,
      change: 2549.2,
      percent: 50.98,
    },
    // Spending that stopped: an improvement of exactly what was being spent.
    {
      categoryId: 5,
      categoryName: 'Transport',
      categoryType: 'EXPENSE',
      current: 0.0,
      previous: -1500.0,
      change: 1500.0,
      percent: 100.0,
    },
    // Income that stopped: the same "nothing this month", read the other way.
    {
      categoryId: 1,
      categoryName: 'Salary',
      categoryType: 'INCOME',
      current: 0.0,
      previous: 95000.0,
      change: -95000.0,
      percent: -100.0,
    },
    // New spending, with no previous month to be a percentage of.
    {
      categoryId: 4,
      categoryName: 'Utilities',
      categoryType: 'EXPENSE',
      current: -4870.0,
      previous: 0.0,
      change: -4870.0,
      percent: null,
    },
    // The refund nothing claimed: every field about its category is null.
    {
      categoryId: null,
      categoryName: null,
      categoryType: null,
      current: 350.0,
      previous: 0.0,
      change: 350.0,
      percent: null,
    },
  ],
};

const TOP_EXPENSES = { from: '2026-11-01', to: '2026-11-30', limit: 10, expenses: [] };

function renderReports() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: 0 } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter>
        <Reports />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

/** The row of the comparison table whose first cell reads `label`. */
async function rowFor(label: string) {
  const cell = await screen.findByRole('cell', { name: label });
  // eslint-disable-next-line testing-library/no-node-access
  const row = cell.closest('tr');
  if (!row) throw new Error(`no row for ${label}`);
  return within(row);
}

beforeEach(() => {
  // `shouldAdvanceTime` keeps the clock fixed for `today()` — the page opens on
  // the current month, and a test that depended on the real one would start
  // failing in December — while still letting real timers run, which
  // `findBy*` needs in order to poll for the rendered rows.
  vi.useFakeTimers({ shouldAdvanceTime: true });
  vi.setSystemTime(new Date('2026-11-21T12:00:00Z'));
  vi.stubGlobal(
    'fetch',
    vi.fn(async (url: string) => {
      const body = url.includes('/reports/comparison') ? COMPARISON : TOP_EXPENSES;
      return {
        ok: true,
        status: 200,
        statusText: 'OK',
        headers: new Headers({ 'Content-Type': 'application/json' }),
        json: async () => body,
      } as unknown as Response;
    }),
  );
});

afterEach(() => {
  vi.useRealTimers();
  vi.unstubAllGlobals();
});

describe('the comparison report', () => {
  it('shows a null percent as an em dash, never as zero', async () => {
    renderReports();
    const row = await rowFor('Utilities');
    // The previous month was zero, which no percentage describes. Rendering it
    // as 0% would claim the spending was unchanged.
    expect(row.getByText('—')).toBeInTheDocument();
    expect(row.queryByText(/0,00%/)).not.toBeInTheDocument();
  });

  it('reads less spending as an improvement, with a plus', async () => {
    renderReports();
    const row = await rowFor('Food');
    // 5000 spent in October, 2450.80 in November. A positive change means the
    // position improved, so this must not read as "spending rose by half".
    expect(row.getByText('+2.549,20')).toBeInTheDocument();
    expect(row.getByText('+50,98%')).toBeInTheDocument();
    expect(row.getByText('less went out')).toBeInTheDocument();
  });

  it('distinguishes spending that stopped from income that stopped', async () => {
    renderReports();
    const transport = await rowFor('Transport');
    const salary = await rowFor('Salary');

    // Both mean "nothing this month". A bus pass that stopped is +100%,
    // because no longer spending that money is an improvement; a salary that
    // stopped is -100%, because losing an income is a loss.
    expect(transport.getByText('+100,00%')).toBeInTheDocument();
    expect(transport.getByText('Transport stopped going out')).toBeInTheDocument();

    expect(salary.getByText('-100,00%')).toBeInTheDocument();
    expect(salary.getByText('Salary stopped coming in')).toBeInTheDocument();
  });

  it('shows the uncategorized row rather than hiding a null category', async () => {
    renderReports();
    // Uncategorized is a real state with real money in it — here a 350 dinar
    // refund no rule could claim. Dropping the row would lose the money.
    const labels = await screen.findAllByText('Uncategorized');
    expect(labels.length).toBeGreaterThan(0);
    expect(screen.getByText('+350,00')).toBeInTheDocument();
  });

  it('names both months it is comparing', async () => {
    renderReports();
    expect(await screen.findByText(/November 2026 against October 2026/)).toBeInTheDocument();
  });

  it('offers the export as a link the browser follows itself', async () => {
    renderReports();
    const link = await screen.findByRole('link', { name: /Download November 2026 as CSV/ });
    // A real href, not an onClick: the browser then honours the server's
    // Content-Disposition and the byte-order mark in the file.
    expect(link).toHaveAttribute('href', '/api/reports/monthly/export?year=2026&month=11');
  });
});
