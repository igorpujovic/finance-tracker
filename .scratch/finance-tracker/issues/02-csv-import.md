# 02 — CSV import: upload, parsing, validation, duplicates, summary

Status: ready-for-agent
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
