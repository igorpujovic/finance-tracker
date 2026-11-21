# 05 — Reports: monthly, comparison, top expenses, CSV export

Status: resolved
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

## Comments

### The -100% contradicts the percent formula stated beside it

This issue contradicts itself, and ADR-0002 settles it.

The Comparison bullet in Scope says, in one sentence: ``percent = change / ABS(previous)``,
empty when previous is zero; a category with nothing this month shows -100%. Those two clauses
cannot both hold. With `change = current - previous`, a category with spending last month and
none this month — Transport at `-5000.00`, then nothing — gives
`change = 0 - (-5000) = +5000` and `percent = +5000 / 5000 = `**`+100%`**. The "Done when"
line repeats the -100%, so the figure appears twice, but both times beside the `ABS` that
contradicts it.

Reaching -100% would mean dropping the `ABS` and letting the divisor keep the sign of
`previous`. That same change makes ADR-0002's own worked example (Food from `-23450.00` to
`-2450.80`) come out at **-89.6%** where the ADR states **+89.6%**, and it breaks the ADR's
stated reason for the whole convention: *"a positive change always means the position
improved."*

So `ABS(previous)` is what was implemented. The -100% reads as having been written with an
income category in mind, where a stopped category genuinely does come out at -100%.

Both cases are covered by name, so neither reading can regress silently:

- `ComparisonTest.anIncomeCategoryThatStoppedIsMinusOneHundredPercent` — Salary, income last
  month and none this month, asserts **-100.00**, which is the literal "Done when".
- `ComparisonTest.anExpenseCategoryThatStoppedIsPlusOneHundredPercent` — Transport, spending
  last month and none this month, asserts **+100.00**, with the reason in the test's javadoc.
- `ComparisonTest.spendingLessIsAnImprovement` — ADR-0002's worked example to the digit,
  **+89.55%**.

### PostgreSQL refuses the full outer join this report is specified as

`FULL JOIN is only supported with merge-joinable or hash-joinable join conditions`.

The uncategorized bucket groups under a null `category_id`, and `null = null` is not true, so a
full outer join on equality splits it into two half-rows — one with a zero current, one with a
zero previous. The totals still add up, which is what makes it hard to see. The condition that
joins a null to itself is `is not distinct from`, and PostgreSQL 18 rejects it in a `FULL JOIN`
outright (verified against the version the suite runs).

`ReportRepository.comparison` therefore gets full-outer-join semantics from the union of both
months' key sets plus a left join to each side. `union` already treats two nulls as one key, so
no sentinel id has to stand in for "no category". `ComparisonTest.uncategorizedJoinsToItself`
asserts the single row.

### Decisions the spec left open

Recorded because a reader of the spec would not be able to predict them.

**The uncategorized row in the export.** Scope fixes the columns (`category,type,total_rsd`)
but not what a row with no category puts in the first two. The JSON leaves all three category
fields null, which is the truth of it — money with no category, per GLOSSARY *Uncategorized* —
but a blank leading cell in a spreadsheet reads as a row somebody forgot to fill in. So the CSV
writes `Uncategorized` for the name and an empty `type`, since the bucket can hold income and
expenses at once and has no type to name (ADR-0002). `MonthlyReportCsv.UNCATEGORIZED` is the
single source of that label, and `MonthlyExportTest` asserts both cells.

**Rejected input.** The spec specifies no validation. Four guards were added anyway, each
turning a 500 into a 400 that says why: a month outside 1–12, an implausible year, a `limit`
below 1, and a window whose `to` precedes its `from`. A `limit` above 100 is capped rather than
refused, per *"capped at 100"*, and `TopExpensesResponse.limit` echoes the limit actually
applied so that a caller who asked for 500 is not left guessing whether only 100 expenses
existed.

**UTF-8 is asserted, not just the BOM.** `MonthlyReportCsvTest.writesNonAsciiNamesAsUtf8`
renders a category named `Zdravlje i lečenje` and asserts it reads back under UTF-8 and does
*not* read back under ISO-8859-1 — so the file is genuinely multi-byte rather than ASCII that
happens to pass. The HTTP test covers the other half: the BOM arriving intact proves the body
is written as bytes and never re-encoded by a message converter.
