# 05 — Reports: monthly, comparison, top expenses, CSV export

Status: ready-for-agent
Blocked by: 04

Roadmap step 5. Aggregation in the repository layer, returned as projections. Everything here is
query-shaped, so it is tested against a real database (ADR-0006).

## Scope

**Monthly** — `GET /api/reports/monthly?year=&month=`
- Totals per category, plus income, expenses and balance.
- Uncategorized appears as a row with a null category id, so the rows are a complete partition of
  the month.
- Signed throughout (ADR-0002): expenses come back negative, balance is the sum of the dinar
  amounts, and `income + expenses = balance` holds literally.

**Comparison** — `GET /api/reports/comparison?year=&month=`
- Full outer join over the categories active in either month, zero-filling the missing side, so a
  category that stopped is reported rather than omitted.
- `change = current - previous`; `percent = change / ABS(previous)`, empty when previous is zero;
  a category with nothing this month shows -100%.
- January compares against the previous December.

**Top expenses** — `GET /api/reports/top-expenses?from=&to=&limit=`
- Negative dinar amounts only, ordered ascending — the most negative first. Limit defaults to 10,
  capped at 100. Uncategorized rows included.

**Export** — `GET /api/reports/monthly/export?year=&month=`
- RFC 4180: comma delimiter, dot decimals, UTF-8 with a BOM, `Content-Disposition` naming
  `monthly-report-YYYY-MM.csv`.
- Columns `category,type,total_rsd`. Category rows only — no appended summary block.

## Done when

- A test asserts the monthly invariant: the category totals sum to the balance, with uncategorized
  money present in the month.
- A test asserts a category with spending last month and none this month appears in the comparison
  at -100%.
- A test asserts top expenses returns the largest spend first — the assertion that catches an
  `ORDER BY ... DESC` written on signed amounts.
- A test reads the exported CSV back with Commons CSV and asserts the totals match the JSON
  endpoint for the same month.
