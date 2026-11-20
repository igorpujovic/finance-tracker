# 04 — Exchange rates: Kurs client, scheduled job, on-demand backfill, conversion

Status: resolved
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

## Comments

Done. 118 tests green against a real PostgreSQL 18, and the endpoints were also driven
against a running app talking to the real Kurs API, so the client was seen to work against
the live document rather than only against the fake.

`sample.csv` now reports 7 / 4 / 1 / 2. The euro line is the fourth import, categorized
Entertainment by the seeded `NETFLIX` rule — a row that until this step was the third
rejection.

Five things were worth more than the line of code they cost:

- **A transport failure has to leave the rate table alone too.** The first version
  downloaded each missing date and saved it before fetching the next, so a statement
  spanning two dates whose second download failed aborted with 503 having already
  committed the first date's rates. No `ImportBatch` row, so the letter of ADR-0005 held,
  but the database had plainly been touched by an upload the caller was told did nothing.
  Every date is now downloaded before any of it is saved. The retry re-downloads all of
  them, which is the cheaper half of the trade.
- **The rate tests had to commit.** They were `@Transactional` like the rest of the import
  tests, which is the trap `ImportWriterTest` was written to avoid: inside a transaction
  that rolls back regardless, a 503 that wrote nothing is indistinguishable from a 503
  that wrote plenty. `ImportRatesTest` now commits and clears up after itself, so "leaves
  the database untouched" is a claim about committed state.
- **`parity` is really not always 1.** The National Bank quotes the currencies whose unit
  is worth a fraction of a dinar per hundred of them — BEF came back from the live API as
  `exchange_middle` 291.6243 with `parity` 100. Quoted per unit, such a line is worth a
  hundred times too much and looks entirely plausible. The rate is also rounded to the
  column's four decimals *before* it is multiplied, so converting a line now and
  converting it again later from the stored row cannot disagree.
- **`date_from` scatters further than a weekend.** Asking for 2026-11-19 returns that
  day's euro rate alongside a Bulgarian lev rate attributed to 2025-12-31, because the
  bank has not changed it since. So one download writes rows under many dates, and the
  save groups by the attributed date rather than assuming one.
- **A conversion the column cannot hold is the line's own problem.** `amount_rsd` is
  `numeric(12, 2)` with `<> 0`, so a tiny amount at a tiny rate rounds to nothing and a
  huge one overflows — both of which the insert would refuse, rolling back every valid
  line in the file. They are refused per line instead, with their own reasons, for the
  same reason `StatementParser` checks the width of the amount the file gave.

Two deliberate widenings, both of them the above: the two rejection reasons about a dinar
amount that cannot be stored, which the issue does not list, and `spring-boot-restclient`
added to the pom — Boot 4 is modular and `spring-boot-starter-webmvc` ships only the
server half of spring-web, so without it there is no autoconfigured `RestClient.Builder`.

Kurs is faked at the socket with the JDK's own `com.sun.net.httpserver` rather than
WireMock or MockWebServer, because Boot 4.2.0 manages neither: either would have meant a
version pinned by hand to buy stubbing that took sixty lines. It serves a real 503, a real
read timeout and a real malformed body, and counts the dates it was asked about.

The import endpoint was the agreed seam for this step and carries most of the weight, with
two additions made afterwards so that nothing in the step is pinned only by hand:

- `DinarAmountTest` and `PublishedRateTest` are pure JUnit over the conversion arithmetic,
  which is where ADR-0006 puts it. The sharpest of them is that the quotient is rounded
  before it is multiplied: a thousand Belgian francs at the stored 2.9162 is 2916.20,
  where the exact quotient would make it 2916.24, so the test fails if that order is ever
  reversed.
- `ExchangeRateApiTest` covers the refresh endpoint and the scheduled job: rates landing
  under the attributed date, a second refresh keeping nothing and still not answering 409,
  a 503 that stores nothing, and the job swallowing an unreachable bank rather than
  putting a stack trace in the log every working morning the network hiccups.

118 tests, and the refresh endpoint was also driven against the real Kurs API: the first
call stored 40 rates, the second stored 0.
