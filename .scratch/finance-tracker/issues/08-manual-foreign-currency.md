# 08 — The manual endpoint refuses a foreign currency with a message step 4 made false

Status: resolved

Not a roadmap step, and blocked by nothing: everything it would need landed in 04. Found while
writing the README in 06 and recorded there as a deliberate departure rather than fixed in
passing, because the stale sentence is the small half of it — the other half is a decision about
whether the manual path converts at all, which is not 06's to make.

## Symptom

```sh
curl -X POST http://localhost:8080/api/transactions -H 'Content-Type: application/json' \
  -d '{"date":"2026-11-04","description":"NETFLIX.COM","amount":-9.99,"currency":"EUR"}'
```

```json
{"status":400,"error":"Bad Request",
 "message":"Manual transactions are limited to RSD until exchange rates arrive; got EUR"}
```

The rates arrived in step 4. `ExchangeRateService.resolve` resolves a currency on a date from the
stored rates or from the National Bank, `ResolvedRates.dinarAmountOf` converts, and the same
euro line in `sample.csv` imports and converts — the import path is simply the only caller. The
refusal is `TransactionService.requireDinar`, and its javadoc still says "resolving those is
step 4".

Four places repeat the claim, so fixing one and not the rest leaves the project arguing with
itself:

- `TransactionService.requireDinar` — the message and the javadoc above it.
- `README.md`, under Known limitations, which already says the message is out of date.
- `TransactionApiTest.rejectsForeignCurrencyForNow`, whose `@DisplayName` ends "until rates
  arrive in step 4".
- `.scratch/finance-tracker/issues/06-quality.md`, which filed it here.

## Two things that are worse than the message

**An imported foreign row cannot be corrected.** `update` runs the same check as `create`, so
`PUT /api/transactions/{id}` on the euro line the importer wrote is a 400 even when the request
hands back the currency the row already holds. The only accepted edit is one claiming the row was
in dinars, and that claim converts at 1: `dinarAmountOf` returns the foreign amount unchanged, so
amending the description of a −9.99 EUR row would silently restate it as −9.99 RSD, about a
hundredth of what was spent. The message says "manual transactions", but the check governs
amendments to imported ones too.

**The non-dinar arm of `dinarAmountOf` is a trap, not a fallback.** It returns null, and
`transactions.amount_rsd` is `numeric(12, 2) not null` — so lifting `requireDinar` on its own
turns a 400 into a `DataIntegrityViolationException`. Whoever takes this should delete that arm
rather than rely on it.

## The decision this needs

**Option A — wire the manual path to the rates.** `create` and `update` resolve the one
`(currency, date)` pair through `ExchangeRateService` before touching the database, exactly as
`StatementConversion` does for a statement's worth of pairs, and store the converted
`amount_rsd`. The three outcomes of 04 then have to be answered for a single line rather than a
file (ADR-0005): a reachable bank with no published rate for that currency on or before that
date, a bank that cannot be reached at all, and a conversion `numeric(12, 2)` cannot hold. Each
already has a reading on the import path, and the first and third are per-line there — which for
a request of one line means 400 with the reason, while an unreachable bank is the 503 the handler
already maps. The widening is that `POST /api/transactions` may now make an HTTP call for a date
nobody has fetched yet; it stays outside the transaction, which is the whole of ADR-0005, and
`@Transactional` sits on the class, so the resolution has to happen before the service method
that writes — not inside it.

**Option B — keep the refusal and fix only the words.** Drop the promise from the message, the
javadoc and the test name, and keep the README limitation with an honest reason instead of a
stale one. Cheap, and leaves a ledger whose two entry points disagree about what a currency is,
and leaves the imported-row edit broken.

A is recommended: the sample statement already produces converted euro rows, so the data model
holds foreign transactions today and only the manual door is shut. B is worth taking only if the
answer is that manual entry is deliberately dinar-only forever, in which case the imported-row
edit still needs fixing separately and this ticket splits.

## Done when

Under A:

- `POST /api/transactions` with a foreign currency and an amount stores a row whose `amount_rsd`
  is the amount converted at the rate in force on the transaction's own date, and the response
  carries it.
- A Sunday foreign transaction converts at Friday's rate, pinned by a test — the same rule 04
  proved for the import path, asserted on this one rather than assumed from it.
- A currency the National Bank does not publish is a 400 naming the currency and the date; a bank
  that cannot be reached is a 503 and writes nothing; a conversion too large or too small for
  `numeric(12, 2)` is a 400 naming the amount, the currency and the date.
- No HTTP call is made inside a database transaction. A test asserts the request that creates a
  row for a date already on file makes none at all.
- `PUT` on an imported foreign row round-trips: fetch it, change the description, send it back
  with the currency it came with, and the amount in dinars is unchanged.
- The dead non-dinar arm of `dinarAmountOf` is gone.
- Every place listed under Symptom says the same thing about what the API does, the README
  limitation included — removed under A, reworded under B.

## Comments

Filed from 06. Nothing has been changed yet; `main` is as described above.

Done under **Option A**. The manual path converts, and it converts through the same object the
import path does.

### What moved

- **`Conversion` lives in `rates` now, not `imports`.** It was the import's word for the outcome of
  converting one amount, and the outcome of converting one amount is not a fact about importing.
- **`ResolvedRates.conversionOf(amount, spent)` is the one reading of the three outcomes.** The
  rate lookup, the dinar-or-nothing rounding check and the column-width check were
  `StatementConversion.storable`, reachable only from an upload. Both paths now ask the same
  method and get the same words back, which is why `no exchange rate for ZWL on 2026-11-04` is one
  string in one place rather than two that have to be kept in step. `DinarAmount` gained
  `fitsTheColumn`, so both facts about `numeric(12, 2)` — its scale and its ten whole digits — sit
  in the class that already knew the scale. `StatementConversion` keeps only its reason to exist:
  handing the whole set of pairs over at once.
- **`TransactionWriter` is a new bean, and that is the whole of ADR-0005 on this path.**
  `@Transactional` was on the `TransactionService` class, so resolving a rate anywhere inside
  `create` or `update` would have made the HTTP call the ADR exists to keep out of a held
  transaction — and a self-call would not have been advised anyway. The write moved to its own
  object, the way `ImportWriter` did, and the dinar amount arrives as an argument: the writer
  cannot reach a rate, which is the guarantee rather than an oversight. The service keeps the two
  read methods and the resolution.
- **`requireDinar` and the null-returning arm of `TransactionService.dinarAmountOf` are both
  gone**, not reworded. Nothing returns null into a `not null` column any more.

### The four places

The message and its javadoc went with `requireDinar`. `TransactionApiTest`'s
`rejectsForeignCurrencyForNow` is replaced by `amendsADinarTransaction` — `PUT` had no test at all
before, on any currency. The README limitation is removed and the exchange-rate section says what
both entry points now do; the 400 example in the error section was the stale message and is now a
real one. 06's deliberate-departures bullet records that it was filed here and fixed here.

### Verified, not assumed

Full suite: **174 tests, no failures** (163 before; eleven new, one replaced). The new ones are
`TransactionRatesTest`, driven through `POST` and `PUT /api/transactions` against a real
PostgreSQL and the HTTP fake of ADR-0006, deliberately not `@Transactional` for the reason
`ImportRatesTest` is not:

- a euro line converts at the rate for its own date, −9.99 EUR at 117.2061 to −1170.89 RSD;
- a Sunday converts at Friday's rate and stores nothing under the Sunday — rule 11 asserted on
  this path rather than assumed from the import path, which is the point of the ticket;
- a date already on file asks Kurs nothing at all, and a dinar amount asks nothing either;
- `ZWL` is a 400 naming the currency and the date; `9999999999.99 EUR` and `-0.01 IDR` are 400s
  naming the amount, the currency and the date;
- a 503 from the bank is a 503 from the API with no row written — which this test can tell apart
  from a row written and rolled back, because it commits for real;
- the imported euro row round-trips through `PUT` in the currency it came with, read back from a
  second request rather than from the response the amendment returned. That last detail matters:
  the response is built from the entity the request held, so without the follow-up `GET` the test
  would pass on an amendment that was never flushed.

Not done, and deliberate: no new ADR. Nothing here contradicts ADR-0005 or widens it — the manual
path is one more caller resolving before it writes, which is what the ADR already decided.

### What the review found, and what it changed

A two-axis review ran against this file before the work was committed. One real regression, which
the "Done when" list would not have caught:

- **`PUT` on an identifier that does not exist answered 400, not 404 — and reached the National
  Bank on behalf of a row that was not there.** Resolving the rate before the write opens is the
  ADR, but it also put the conversion ahead of the lookup, so a `PUT /api/transactions/999999`
  carrying a currency with no published rate argued about the currency of a missing transaction.
  `update` now asks whether the row exists first, and a test asserts the 404 *and* that Kurs was
  asked nothing. There was no `PUT`-404 test on any path before, which is why nothing caught it.
- **`DinarAmount.fitsTheColumn` was pinned only through a container.** ADR-0006 keeps the
  conversion arithmetic in pure JUnit, and the ten-whole-digit boundary is now asserted there on
  both sides of itself.
- **Prose called a hand-entered transaction a "line".** GLOSSARY scopes *source line* to a row of
  an uploaded statement, so the wording in `Conversion`, `ResolvedRates`, `TransactionService` and
  the README now says *transaction* where it means one.
- **01-setup.md** carried the same stale promise the four listed places did. Not on the list, but
  leaving it would have been the same disease, so it now points here too.

Considered and declined, with reasons, rather than silently:

- **`TransactionWriter` took `changeCategory` and `delete` as well as the two methods that
  convert.** Read as scope creep by one axis. The class-level `@Transactional` had to come off
  `TransactionService` for the ADR, which leaves those two needing a boundary somewhere; putting
  them on the writer keeps every boundary in one object instead of scattering `@Transactional`
  back across the service it was just removed from.
- **The single-line converter was lifted into `rates` rather than copied from
  `StatementConversion`.** Read as refactoring a working path. The ticket's own complaint is four
  places repeating a claim and arguing with each other; duplicating the three outcomes and their
  wording would have been a fifth and a sixth.
- **`TransactionWriter.find` repeats the body of `TransactionService.get`.** Three lines, in two
  beans that cannot depend on each other without a cycle.
- **`TransactionRatesTest` repeats `ImportRatesTest`'s cleanup and its date constants.** Hoisting
  them into `PostgresTest` would hand a commit-and-delete lifecycle to twenty test classes that
  roll back instead and need none of it.
- **`PUT` with `categoryId: null` still clears a category a rule assigned.** True, and now
  reachable on an imported row. It is the documented meaning of a full replacement on this API
  and it predates this ticket; it is not a currency question and is left alone deliberately.
