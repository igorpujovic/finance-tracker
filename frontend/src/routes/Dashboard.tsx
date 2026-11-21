import { useState } from 'react';
import { Link } from 'react-router-dom';
import {
  Bar,
  BarChart,
  LabelList,
  ReferenceLine,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { useMonthlyReport } from '../api/hooks';
import type { CategoryTotal } from '../api/types';
import { amount, categoryName, monthLabel, today } from '../format';
import { Money } from '../components/Money';
import { MonthPicker, TypeBadge } from '../components/Controls';
import { Empty, ErrorNotice, Loading } from '../components/Notices';

/**
 * One month's position: the three totals, the categories that make them up, and
 * the same breakdown as a chart.
 *
 * The chart is a zero-baseline horizontal bar chart, and that form is chosen for
 * what the data *is* rather than for variety. Category totals are signed
 * (ADR-0002), so they straddle zero: expenses run left of the baseline and
 * income runs right, which means the two kinds are told apart by position before
 * any colour is involved. A pie chart cannot do this at all — negative slices
 * are meaningless — and would also have to hide the long category names this one
 * puts on an axis.
 */
export function Dashboard() {
  const now = today().split('-');
  const [year, setYear] = useState(Number(now[0]));
  const [month, setMonth] = useState(Number(now[1]));

  const report = useMonthlyReport(year, month);

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Dashboard</h1>
          <p>
            One month's income, expenses and balance. Expenses are negative, so the three totals
            add up literally: income + expenses = balance.
          </p>
        </div>
        <MonthPicker
          year={year}
          month={month}
          onChange={(y, m) => {
            setYear(y);
            setMonth(m);
          }}
        />
      </div>

      <ErrorNotice error={report.error} />

      {report.isPending && <Loading what="Reading the month" />}

      {report.data && (
        <>
          <div className="grid grid-3" style={{ marginBottom: '1rem' }}>
            <div className="card stat">
              <div className="label">Income</div>
              <div className="value">
                <Money value={report.data.income} withCurrency />
              </div>
            </div>
            <div className="card stat">
              <div className="label">Expenses</div>
              <div className="value">
                <Money value={report.data.expenses} withCurrency />
              </div>
            </div>
            <div className="card stat">
              <div className="label">Balance</div>
              <div className="value">
                <Money value={report.data.balance} withCurrency />
              </div>
            </div>
          </div>

          <div className="grid grid-2">
            <CategoryChart categories={report.data.categories} year={year} month={month} />
            <CategoryTable report={report.data} />
          </div>
        </>
      )}
    </>
  );
}

interface Row {
  name: string;
  total: number;
  /**
   * The bar's length, and nothing else.
   *
   * A bar cannot be drawn with a negative length, so geometry needs a
   * magnitude — but no *figure* shown to a reader is ever stripped of its sign:
   * the label on the bar, the tooltip and the table all render `total`. The
   * direction the magnitude belongs to is carried by which of the two panels
   * the bar is in, which is why the panels are headed "where money went" and
   * "where money came from" rather than left unlabelled.
   */
  magnitude: number;
  uncategorized: boolean;
}

function rowsOf(categories: CategoryTotal[]): Row[] {
  return (
    categories
      .map((category) => ({
        name: categoryName(category.categoryName),
        total: category.totalRsd,
        magnitude: Math.abs(category.totalRsd),
        uncategorized: category.categoryId === null,
      }))
      // Largest expense at the top down to largest income at the bottom, so the
      // bars fan out from the baseline in one direction rather than zig-zagging.
      .sort((a, b) => a.total - b.total)
  );
}

/**
 * Expenses and income are drawn as two panels, not as one chart around a zero
 * baseline — which is what this was first written as, and it was wrong.
 *
 * A month's salary is an order of magnitude larger than any single category of
 * spending: on one shared scale, 95.000 of income flattens −4.870 of utilities
 * into a sliver two pixels wide, and the chart answers no question at all. Two
 * measures of such different size get two panels, each scaled to its own
 * largest bar, rather than one axis that neither of them fits. The alternative
 * of a second x-axis is not one: a dual-scale chart invites exactly the
 * comparison it has made meaningless.
 *
 * Separating them costs nothing in meaning, because the two panels are the two
 * sides of the ledger, and the sign is still printed on every bar. It also
 * keeps the colour pair legible without relying on colour: the panels are
 * separated by position and heading before green and red are involved, which is
 * the secondary encoding the measured CVD separation of those two hues requires.
 */
function CategoryChart({
  categories,
  year,
  month,
}: {
  categories: CategoryTotal[];
  year: number;
  month: number;
}) {
  const rows = rowsOf(categories);
  const expenses = rows.filter((row) => row.total < 0);
  // Largest first within each panel, so both read top-down by size.
  const income = rows.filter((row) => row.total >= 0).sort((a, b) => b.total - a.total);

  if (rows.length === 0) {
    return (
      <div className="card">
        <h2>By category</h2>
        <Empty>Nothing was recorded in {monthLabel(year, month)}.</Empty>
      </div>
    );
  }

  return (
    <div className="card">
      <h2>By category</h2>
      <div className="card-body">
        {expenses.length > 0 && (
          <CategoryBars title="Where money went" rows={expenses} tone="expense" />
        )}
        {income.length > 0 && (
          <CategoryBars title="Where money came from" rows={income} tone="income" />
        )}
        <p className="hint">
          Each panel is scaled to its own largest bar, so the two are not to the same scale —
          every bar carries its own total. Hover one for a tooltip.
        </p>
      </div>
    </div>
  );
}

function CategoryBars({
  title,
  rows,
  tone,
}: {
  title: string;
  rows: Row[];
  tone: 'income' | 'expense';
}) {
  const fill = tone === 'income' ? 'var(--income-mark)' : 'var(--expense-mark)';
  // Enough room per bar that the labels never collide, plus the heading.
  const height = rows.length * 34 + 16;

  return (
    <div style={{ marginBottom: '0.9rem' }}>
      <h3
        style={{
          fontSize: '0.78rem',
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          color: 'var(--text-muted)',
          marginBottom: '0.3rem',
        }}
      >
        {title}
      </h3>
      <div style={{ width: '100%', height }}>
        <ResponsiveContainer width="100%" height="100%">
          <BarChart data={rows} layout="vertical" margin={{ top: 2, right: 84, bottom: 2, left: 2 }}>
            {/* No grid and no x-axis ticks: every bar is directly labelled, so a
                reader never has to measure one against a gridline. Both panels
                grow left to right from a common origin — the axis is not
                reversed for expenses, because a mirrored panel leaves its
                labels outside the plot area with nowhere to render. */}
            <XAxis type="number" hide />
            <YAxis
              type="category"
              dataKey="name"
              width={104}
              tickLine={false}
              axisLine={false}
              tick={{ fontSize: 12, fill: 'var(--text-muted)' }}
            />
            <ReferenceLine x={0} stroke="var(--border-strong)" />
            <Tooltip
              cursor={{ fill: 'var(--accent-soft)' }}
              contentStyle={{
                background: 'var(--surface-raised)',
                border: '1px solid var(--border-strong)',
                borderRadius: 'var(--radius-sm)',
                color: 'var(--text)',
                fontSize: '0.85rem',
              }}
              // Reads `total` off the row rather than the bar's own value, so
              // the tooltip shows the signed amount and not the bar's length.
              formatter={(_value: unknown, _name: unknown, item: { payload?: Row }) => [
                `${amount(item?.payload?.total ?? 0)} RSD`,
                'Total',
              ]}
            />
            <Bar dataKey="magnitude" radius={4} barSize={18} isAnimationActive={false} fill={fill}>
              {/* The second encoding the measured colour separation requires:
                  every bar carries its own signed number, so the chart is
                  readable with no colour perception at all. Labelled from
                  `total`, so what is printed keeps the sign the bar's length
                  cannot. */}
              <LabelList
                dataKey="total"
                position="right"
                formatter={(value: unknown) => amount(Number(value))}
                style={{ fontSize: 11, fill: 'var(--text-muted)' }}
              />
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

function CategoryTable({
  report,
}: {
  report: { categories: CategoryTotal[]; balance: number; year: number; month: number };
}) {
  if (report.categories.length === 0) {
    return (
      <div className="card">
        <h2>Totals</h2>
        <Empty>
          No transactions yet. <Link to="/import">Import a statement</Link> to get started.
        </Empty>
      </div>
    );
  }

  return (
    <div className="card">
      <h2>Totals</h2>
      <div className="table-wrap">
        <table>
          <caption className="sr-only">
            Category totals for {monthLabel(report.year, report.month)}, in dinars
          </caption>
          <thead>
            <tr>
              <th scope="col">Category</th>
              <th scope="col">Type</th>
              <th scope="col" className="num">
                Total (RSD)
              </th>
            </tr>
          </thead>
          <tbody>
            {rowsOf(report.categories).map((row) => (
              <tr key={row.name}>
                <td>
                  {row.uncategorized ? <span className="badge uncategorized">{row.name}</span> : row.name}
                </td>
                <td>
                  <TypeBadge type={row.total >= 0 ? 'INCOME' : 'EXPENSE'} />
                </td>
                <td className="num">
                  <Money value={row.total} />
                </td>
              </tr>
            ))}
          </tbody>
          {/*
            The footer is the project's own arithmetic, shown rather than
            asserted: the category rows account for every transaction in the
            month, uncategorized ones included, so they sum to the balance.
          */}
          <tfoot>
            <tr>
              <td colSpan={2}>Balance</td>
              <td className="num">
                <Money value={report.balance} />
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  );
}
