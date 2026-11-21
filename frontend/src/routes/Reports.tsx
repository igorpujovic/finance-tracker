import { useState } from 'react';
import { downloadUrl } from '../api/client';
import { useComparison, useTopExpenses } from '../api/hooks';
import type { ComparisonRow } from '../api/types';
import {
  categoryName,
  date as formatDate,
  monthBounds,
  monthLabel,
  percent,
  today,
} from '../format';
import { Money } from '../components/Money';
import { CategoryLabel, MonthPicker, TypeBadge } from '../components/Controls';
import { Empty, ErrorNotice, Loading } from '../components/Notices';

/**
 * The two reports that are not the dashboard, and the CSV export.
 *
 * The comparison is the one that needs reading carefully, and the page says so
 * rather than leaving it to be discovered. `change` is `current - previous` on
 * signed amounts, so a **positive change always means the position improved** —
 * spending less on Food and earning more Salary are the same arithmetic and the
 * same sign. One rule covers both sides of the ledger with no exceptions, and
 * the price is that "+50.98%" on an expense row has to be read as "half as much
 * went out", not as "spending rose".
 */
export function Reports() {
  const now = today().split('-');
  const [year, setYear] = useState(Number(now[0]));
  const [month, setMonth] = useState(Number(now[1]));

  const bounds = monthBounds(year, month);
  const [from, setFrom] = useState(bounds.from);
  const [to, setTo] = useState(bounds.to);
  const [limit, setLimit] = useState(10);

  const comparison = useComparison(year, month);
  const topExpenses = useTopExpenses(from, to, limit);

  const changeMonth = (nextYear: number, nextMonth: number) => {
    setYear(nextYear);
    setMonth(nextMonth);
    // The top-expenses window follows the month being looked at, because
    // comparing November while ranking October's expenses is a report nobody
    // asked for. Both inputs stay editable afterwards.
    const next = monthBounds(nextYear, nextMonth);
    setFrom(next.from);
    setTo(next.to);
  };

  return (
    <>
      <div className="page-head">
        <div>
          <h1>Reports</h1>
          <p>
            A month against the one before it, the largest expenses in a window, and the monthly
            report as a file.
          </p>
        </div>
        <MonthPicker year={year} month={month} onChange={changeMonth} />
      </div>

      <div className="card" style={{ marginBottom: '1.25rem' }}>
        <h2>Export</h2>
        <div className="card-body">
          {/*
            A plain link, not a fetch. The browser then honours the server's
            Content-Disposition and keeps the byte-order mark the export
            deliberately carries so that Serbian category names survive a
            double-click into Excel — both of which a blob round-trip through
            JavaScript is liable to lose.
          */}
          <a
            className="button"
            href={downloadUrl('/reports/monthly/export', { year, month })}
            download
          >
            Download {monthLabel(year, month)} as CSV
          </a>
          <p className="hint">
            RFC 4180 with a UTF-8 byte-order mark. Category rows only — a summary block appended to
            a CSV makes it a file nothing can read as a CSV.
          </p>
        </div>
      </div>

      <h2 style={{ marginBottom: '0.75rem' }}>
        {monthLabel(year, month)} against{' '}
        {comparison.data
          ? monthLabel(comparison.data.previousYear, comparison.data.previousMonth)
          : 'the month before'}
      </h2>

      <ErrorNotice error={comparison.error} />

      <div className="card" style={{ marginBottom: '1.25rem' }}>
        {comparison.isPending && <Loading what="Comparing" />}

        {comparison.data?.categories.length === 0 && (
          <Empty>Neither month had any activity.</Empty>
        )}

        {comparison.data && comparison.data.categories.length > 0 && (
          <>
            <div className="table-wrap">
              <table>
                <thead>
                  <tr>
                    <th scope="col">Category</th>
                    <th scope="col">Type</th>
                    <th scope="col" className="num">
                      This month
                    </th>
                    <th scope="col" className="num">
                      Previous
                    </th>
                    <th scope="col" className="num">
                      Change
                    </th>
                    <th scope="col" className="num">
                      Percent
                    </th>
                    <th scope="col">Reads as</th>
                  </tr>
                </thead>
                <tbody>
                  {[...comparison.data.categories]
                    .sort((a, b) => a.change - b.change)
                    .map((row) => (
                      <tr key={`${row.categoryId ?? 'uncategorized'}-${row.categoryName}`}>
                        <td>
                          <CategoryLabel name={row.categoryName} />
                        </td>
                        <td>
                          <TypeBadge type={row.categoryType} />
                        </td>
                        <td className="num">
                          <Money value={row.current} />
                        </td>
                        <td className="num">
                          <Money value={row.previous} />
                        </td>
                        <td className="num">
                          <Money value={row.change} signed />
                        </td>
                        <td className="num">
                          {/* Null means the previous month was zero, which no
                              percentage describes — shown as an em dash, never
                              as 0% and never as infinity. */}
                          <span
                            className={
                              row.percent === null
                                ? 'money'
                                : row.percent >= 0
                                  ? 'money income'
                                  : 'money expense'
                            }
                          >
                            {percent(row.percent)}
                          </span>
                        </td>
                        <td>
                          <Verdict row={row} />
                        </td>
                      </tr>
                    ))}
                </tbody>
              </table>
            </div>
            <p className="hint" style={{ padding: '0 1rem 0.85rem' }}>
              A positive change means the position improved, on both sides of the ledger: for an
              expense category it means less went out. A category active in either month appears, so
              one that stopped is as visible as one that started — and it shows −100% if it was
              income lost, or +100% if it was spending that ended.
            </p>
          </>
        )}
      </div>

      <h2 style={{ marginBottom: '0.75rem' }}>Largest expenses</h2>

      <div className="card" style={{ marginBottom: '1rem' }}>
        <div className="card-body filters">
          <div>
            <label htmlFor="te-from">From</label>
            <input
              id="te-from"
              type="date"
              value={from}
              onChange={(event) => setFrom(event.target.value)}
            />
          </div>
          <div>
            <label htmlFor="te-to">To</label>
            <input
              id="te-to"
              type="date"
              value={to}
              onChange={(event) => setTo(event.target.value)}
            />
          </div>
          <div>
            <label htmlFor="te-limit">How many</label>
            <input
              id="te-limit"
              type="number"
              min={1}
              max={100}
              value={limit}
              onChange={(event) => setLimit(Number(event.target.value))}
            />
          </div>
        </div>
      </div>

      <ErrorNotice error={topExpenses.error} />

      <div className="card">
        {topExpenses.isPending && <Loading what="Ranking" />}

        {topExpenses.data?.expenses.length === 0 && (
          <Empty>Nothing went out between those dates.</Empty>
        )}

        {topExpenses.data && topExpenses.data.expenses.length > 0 && (
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th scope="col">Date</th>
                  <th scope="col">Description</th>
                  <th scope="col">Category</th>
                  <th scope="col" className="num">
                    Amount (RSD)
                  </th>
                </tr>
              </thead>
              <tbody>
                {topExpenses.data.expenses.map((expense) => (
                  <tr key={expense.id}>
                    <td style={{ whiteSpace: 'nowrap' }}>{formatDate(expense.date)}</td>
                    <td>{expense.description}</td>
                    <td>
                      <CategoryLabel name={expense.categoryName} />
                    </td>
                    <td className="num">
                      <Money value={expense.amountRsd} />
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        <p className="hint" style={{ padding: '0.5rem 1rem 0.85rem' }}>
          Negative amounts only, uncategorized included. The window is inclusive at both ends.
        </p>
      </div>
    </>
  );
}

/**
 * The sentence the numbers mean, written out.
 *
 * This column exists because `+50.98%` on a Food row is the one figure in the
 * whole project a reader is likely to get backwards, and a word next to it costs
 * less than a footnote they will not read. The wording distinguishes a category
 * that *stopped* from one that merely shrank, because the report deliberately
 * reports both and the sign alone does not say which.
 */
function Verdict({ row }: { row: ComparisonRow }) {
  const name = categoryName(row.categoryName);
  const income = row.categoryType === 'INCOME';

  if (row.current === 0 && row.previous !== 0) {
    return (
      <span className="hint" style={{ margin: 0 }}>
        {income ? `${name} stopped coming in` : `${name} stopped going out`}
      </span>
    );
  }
  if (row.previous === 0 && row.current !== 0) {
    return (
      <span className="hint" style={{ margin: 0 }}>
        new this month
      </span>
    );
  }
  if (row.change === 0) {
    return (
      <span className="hint" style={{ margin: 0 }}>
        unchanged
      </span>
    );
  }
  const better = row.change > 0;
  return (
    <span className="hint" style={{ margin: 0 }}>
      {income
        ? better
          ? 'more came in'
          : 'less came in'
        : better
          ? 'less went out'
          : 'more went out'}
    </span>
  );
}
