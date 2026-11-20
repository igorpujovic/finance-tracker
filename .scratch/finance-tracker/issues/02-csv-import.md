# 02 — CSV import: upload, parsing, validation, duplicates, summary

Status: resolved
Blocked by: 01

Roadmap step 2. The pipeline, minus categorization and currency conversion, which arrive in 03
and 04 and plug into the seams left here.

## Scope

- `POST /api/imports`, multipart. Reject non-`.csv` and files over 5 MB with 400. Reject a missing
  or wrong header with 400 before anything is written.
- Parse with Apache Commons CSV so `"NETFLIX.COM, AMSTERDAM"` survives. Column mapping lives in
  one class.
- Validate each line: date `dd.MM.yyyy` and real (`31.11.2026` is not), amount parses and is
  non-zero, currency is three letters, description non-empty. Failures become `ImportError` rows
  carrying the raw line and a reason, and never stop the import.
- Fingerprint each valid line from date, description, amount and currency.
- **Duplicate detection runs against the database and within the file** (ADR-0003): one
  `WHERE fingerprint IN (:all)` lookup up front, plus a `Set` accumulating fingerprints seen
  earlier in this file. The unique index is a backstop that should never fire.
- One transaction writes the valid rows, the errors and the batch summary.
- `GET /api/imports`, `GET /api/imports/{id}` with its errors.

## Seams for later steps

- Conversion: for now, `RSD` lines convert at 1 and non-RSD lines are rejected with reason
  "no exchange rate". Step 04 replaces this with the rate pre-pass.
- Categorization: leave `category_id` null. Step 03 inserts rule matching before persistence.

## Done when

- Importing `sample.csv` yields total 7, imported 3, duplicate 1, rejected 3 — the EUR line being
  the third rejection until step 04, after which it becomes imported 4, rejected 2.
- A test asserts `total_rows = imported_rows + duplicate_rows + rejected_rows`.
- A test imports the same file twice and asserts the second import adds nothing and reports every
  line as a duplicate.

## Comments

Done. 63 tests green against a real PostgreSQL 18, and the endpoints were also driven against a
running app so that the writes were seen to commit rather than only to roll back at the end of a
test.

Importing `sample.csv` reports 7 / 3 / 1 / 3 as specified. The EUR line is the third rejection,
with the reason naming the currency and the date it wanted a rate for, so that step 04 can be
seen to have landed by that line turning into the fourth import.

Four things were worth more than the line of code they cost:

- **The build was dropping a class.** With `maven-compiler-plugin`'s incremental check on, one
  `mvn clean test` compiles all 43 sources, reports success, and writes 42 class files: a class
  that reads a generated `*_` metamodel is silently left out, and which one varies between runs.
  The application then starts without it, so the symptom was `ImportController` missing and every
  `/api/imports` route answering 404 — in a build that passed. Fixed with
  `<incrementalCompilation>none</incrementalCompilation>`, committed separately since it predates
  this step and would have bitten any of them.
- **A duplicate has to be a line already imported.** Marking fingerprints as seen while sieving,
  before conversion ran, made the second of two identical EUR lines a duplicate of a line that
  was itself rejected: `rejected_rows` counted one bad line where there were two, and the second
  had no error row to explain it. Conversion is now resolved once per fingerprint and a copy of
  an unconvertible line is rejected on its own terms. Costs no extra rate lookups, because lines
  sharing a fingerprint share a date and a currency too.
- **`row_number` is the line in the file, so it has to be counted from the file.** Numbering by
  row index drifts the moment a blank line or a quoted newline appears, which is exactly when
  somebody needs to find the line. It is counted from the character positions Commons CSV
  reports, stepping over the line breaks a skipped blank line leaves behind.
- **Atomicity needed a test that commits.** Every other import test is `@Transactional`, which
  wraps the writer and makes a writer that opens no transaction look identical to one that does.
  `ImportWriterTest` is deliberately not transactional and forces the fingerprint unique index —
  the ADR-0003 backstop — to fire mid-write, then asserts no batch summary survives describing
  rows that were never written. `ImportWriter.write` was also made public: a CGLIB proxy does
  advise a package-private method, but only because the generated subclass lands in the same
  package, and that is not a guarantee worth resting on.

Two deliberate widenings beyond the scope as written. The multipart ceiling moved from 5 MB to
10 MB so that the 5 MB rule is enforced by the importer, with a message naming the limit, rather
than by the container with a generic one; anything past 10 MB is not worth buffering and is
turned into the same 400 by the exception handler. And there are more rejection reasons than the
four listed — a wrong column count, a description or amount too big for its column — because the
issue says a rejected line never stops an import, and a line the database would refuse would
otherwise roll back every valid line in the file.

Carried into the next steps as planned: `category_id` is left null and imported transactions
arrive uncategorized, which the uncategorized filter already covers; `StatementConversion` is the
ADR-0005 pre-pass, holding the shape step 04 fills in — one `Conversion` per line, resolved before
the transaction opens, with an unresolvable currency being the line's own problem.
