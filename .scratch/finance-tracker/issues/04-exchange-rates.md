# 04 — Exchange rates: Kurs client, scheduled job, on-demand backfill, conversion

Status: ready-for-agent
Blocked by: 02

Roadmap step 4. This is the step ADR-0005 is about; read it first.

## Scope

- `RestClient` against the Kurs API, reading `exchange_middle / parity` per currency for a date.
- Store rates under the date the API attributes them to, never the date that was requested.
- Lookup: the most recent rate on or before the transaction's date —
  `WHERE currency = ? AND rate_date <= ? ORDER BY rate_date DESC LIMIT 1`. This is the whole
  weekend-and-holiday rule; no calendar, no special case.
- `RSD` short-circuits to 1 with no lookup and no stored row.
- **Rate pre-pass** replacing the stub from 02: after parsing and validating, collect the distinct
  `(currency, date)` pairs the valid lines need, resolve them all — stored first, API for genuine
  gaps — and only then open the transaction. No HTTP inside the transaction.
- Two failure readings (ADR-0005): a currency the API does not publish rejects that one line with a
  reason naming currency and date; a transport failure aborts the upload with 503 and writes
  nothing, including no `ImportBatch` row.
- Scheduled job on working days saving the day's rates; `POST /api/exchange-rates/refresh` for
  manual use.

## Done when

- `sample.csv` now imports the EUR line: total 7, imported 4, duplicate 1, rejected 2.
- Kurs is mocked at the HTTP boundary, not behind a Java interface (ADR-0006). Tests serve a real
  503, a real timeout and a real malformed body, and assert: 503 leaves the database untouched
  with no batch row; an unpublished currency rejects exactly one line and imports the rest.
- A test asserts a Sunday transaction converts at Friday's rate, and that no rate row is stored
  under the Sunday.
- A test asserts the pre-pass makes one API call per missing date, not one per row.
