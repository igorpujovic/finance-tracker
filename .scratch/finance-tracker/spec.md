# Personal Finance Tracker — Specification

Revised 2026-11-20 from the original project specification, after a design review that changed
seventeen things. Deviations are listed at the bottom, each pointing at the ADR that records why.

## Overview

A REST API that imports bank statements from CSV files, cleans and categorizes the transactions,
and produces monthly spending reports in Serbian dinars.

- **Users**: one. User accounts are out of scope.
- **Shape**: a small ETL pipeline — extract rows from a CSV, validate and transform them, load
  them into PostgreSQL, report on them with SQL aggregations.
- **Stack**: Java 21, Spring Boot 4.2.0, Spring Data JPA, PostgreSQL 18, Flyway, JUnit 5,
  Testcontainers, Docker Compose, GitHub Actions.
- **Repository**: `finance-tracker`, public.

## Data model

Six tables. Transactions are the core; every imported file is recorded as an import batch with
its rejected lines, so each import can be explained later.

### Category

| Field | Type | Notes |
| --- | --- | --- |
| id | bigint | Primary key |
| name | varchar(50) | Required, unique |
| type | enum | `INCOME` or `EXPENSE` |

### Transaction

| Field | Type | Notes |
| --- | --- | --- |
| id | bigint | Primary key |
| date | date | Required |
| description | varchar(255) | Text from the statement |
| amount | numeric(12,2) | Negative = expense, positive = income. `CHECK (amount <> 0)` |
| currency | char(3) | ISO code |
| amount_rsd | numeric(12,2) | Required. Signed, converted at the transaction's date |
| category_id | bigint, nullable | Null = uncategorized |
| source | enum | `MANUAL` or `IMPORT` |
| import_batch_id | bigint, nullable | |
| fingerprint | varchar(64), nullable | Unique. Set for `IMPORT`, null for `MANUAL` |
| created_at | timestamp | Server-set |

There is no `type` column: income and expense are read from the sign of `amount` (ADR-0002).
A check constraint ties source and fingerprint together (ADR-0003).

### CategoryRule

| Field | Type | Notes |
| --- | --- | --- |
| id | bigint | Primary key |
| keyword | varchar(100) | Matched case-insensitively against the description |
| category_id | bigint | |
| priority | int | Higher wins among compatible rules |

### ImportBatch

| Field | Type | Notes |
| --- | --- | --- |
| id | bigint | Primary key |
| file_name | varchar(255) | |
| imported_at | timestamp | Server-set |
| total_rows | int | Data rows in the file |
| imported_rows | int | Saved as transactions |
| duplicate_rows | int | Skipped as already seen |
| rejected_rows | int | Failed validation |

Invariant: `total_rows = imported_rows + duplicate_rows + rejected_rows`.

### ImportError

| Field | Type | Notes |
| --- | --- | --- |
| id | bigint | Primary key |
| import_batch_id | bigint | |
| row_number | int | Line number in the file |
| raw_line | text | The original line, unchanged |
| reason | varchar(255) | e.g. "Invalid date format" |

### ExchangeRate

| Field | Type | Notes |
| --- | --- | --- |
| id | bigint | Primary key |
| currency | char(3) | |
| rate_date | date | The date the rate applies to, as published |
| rate_to_rsd | numeric(12,4) | Value of one unit in RSD |

Unique on `(currency, rate_date)`.

## Features

### Transactions and categories

- CRUD for categories and manual transactions
- List transactions with filters (date range, category, income or expense, uncategorized only),
  sorting and pagination
- Change the category of one transaction

### CSV import

- Upload a statement as a CSV file
- Each line is validated, converted to RSD, categorized and saved; invalid lines become import
  errors instead of stopping the import
- Lines already imported — by an earlier upload, or earlier in the same file — are skipped and
  counted
- The response is an import summary: total, imported, duplicate, rejected
- Import history, and the errors of each import

### Automatic categorization

- Rules such as "description contains MAXI, category Food"
- Applied during import
- Re-run over uncategorized transactions on request

### Reports

- Monthly totals per category, with income, expenses and balance
- Comparison with the previous month per category, in RSD and percent
- Top N largest expenses for a date range
- Monthly report exported as CSV

### Exchange rates

- Daily rates from the Kurs API (official NBS rates, no key)
- A scheduled job refreshes them on working days
- Missing historical dates are fetched on demand during import
- Manual refresh endpoint

## CSV import format

UTF-8, header row, four comma-separated columns.

| Column | Format | Example |
| --- | --- | --- |
| date | dd.MM.yyyy | 03.11.2026 |
| description | text, quoted if it contains a comma | MAXI 245 BEOGRAD |
| amount | decimal with a dot, negative for expenses | -2450.80 |
| currency | 3-letter ISO code | RSD |

The column mapping lives in one class so a different bank's export can be supported later.

### Reference file

`src/test/resources/statements/sample.csv` — one duplicate pair, two invalid lines:

```
date,description,amount,currency
01.11.2026,PLATA OKTOBAR,95000.00,RSD
03.11.2026,MAXI 245 BEOGRAD,-2450.80,RSD
04.11.2026,"NETFLIX.COM, AMSTERDAM",-9.99,EUR
05.11.2026,EPS SNABDEVANJE,-4870.00,RSD
05.11.2026,EPS SNABDEVANJE,-4870.00,RSD
31.11.2026,BUS PLUS DOPUNA,-1500.00,RSD
07.11.2026,LIDL 112,abc,RSD
```

Expected summary: total 7, imported 4, duplicate 1, rejected 2. This is asserted as a test.

## Business rules

Each rule has at least one test.

### Import

1. Only `.csv` files up to 5 MB; a missing or wrong header rejects the whole file with 400.
2. A line is rejected, and stored as an import error, if the date is invalid, the amount is not
   a number or is zero, the currency is not a 3-letter code, or the description is empty.
3. One bad line never stops an import.
4. A line whose fingerprint already exists — in the database or earlier in the same file — is
   skipped as a duplicate. Known limitation: two genuinely identical payments on the same day
   count as one.
5. Saving the valid lines, the errors and the batch summary happens in one database transaction.
   Parsing and rate resolution happen before it opens.

### Categorization

6. Rules match if the description contains the keyword, ignoring case.
7. Only rules whose category type matches the transaction's type are candidates; among those,
   highest priority wins; with no compatible candidate the transaction stays uncategorized.
8. A transaction's type is the sign of its amount. An expense takes only an expense category.
9. A category with transactions or rules cannot be deleted (409).

### Money and currencies

10. `BigDecimal` in Java, `numeric` in the database, never `double`. Amounts are never zero.
11. Foreign amounts convert at the most recent rate on or before the transaction's date.
12. A currency the Kurs API does not publish rejects that line with a reason. A transport failure
    aborts the whole upload with 503 and writes nothing.

### Reports

13. Reports use `amount_rsd`, so currencies sum together.
14. Uncategorized transactions appear as a row with a null category. Category totals always sum
    to the balance.
15. Percent change is empty when the previous month was zero. A category present in either month
    appears; one that stopped shows -100%.
16. Expenses are reported as negative numbers, so income plus expenses equals the balance.

## API endpoints

23 endpoints across six controllers — unchanged from the original specification.

| Method | Route | Purpose |
| --- | --- | --- |
| GET | /api/categories | List |
| POST | /api/categories | Create |
| PUT | /api/categories/{id} | Update |
| DELETE | /api/categories/{id} | Delete an unused category |
| GET | /api/transactions | List with filters |
| GET | /api/transactions/{id} | Get one |
| POST | /api/transactions | Create a manual transaction |
| PUT | /api/transactions/{id} | Update |
| PATCH | /api/transactions/{id}/category | Change only the category |
| DELETE | /api/transactions/{id} | Delete |
| GET | /api/rules | List |
| POST | /api/rules | Create |
| PUT | /api/rules/{id} | Update |
| DELETE | /api/rules/{id} | Delete |
| POST | /api/rules/apply | Apply to all uncategorized |
| POST | /api/imports | Upload a CSV, return the summary |
| GET | /api/imports | List past imports |
| GET | /api/imports/{id} | One import with its errors |
| GET | /api/reports/monthly | Totals per category |
| GET | /api/reports/comparison | Change against the previous month |
| GET | /api/reports/top-expenses | Largest expenses |
| GET | /api/reports/monthly/export | Monthly report as CSV |
| POST | /api/exchange-rates/refresh | Download today's rates |

Query parameters: transactions take `from`, `to`, `categoryId`, `type`, `uncategorized`, `page`,
`size`, `sort`; monthly and comparison take `year` and `month`; top-expenses takes `from`, `to`,
`limit`.

Defaults: transactions page size 20 (max 100), sorted by date descending then id descending;
top-expenses limit 10 (max 100), negative amounts only, uncategorized included.

## Technical requirements

- **Architecture**: Controller to Service to Repository, constructor injection. Packages by
  feature (`category`, `transaction`, `rule`, `importing`, `reporting`, `rates`), not by layer.
- **Database**: PostgreSQL 18; schema only through Flyway, never Hibernate auto-update.
- **DTOs**: Java records; entities never returned directly.
- **Validation**: Bean Validation on request DTOs; 400 with field errors.
- **Error handling**: one `@RestControllerAdvice` returning consistent JSON (400, 404, 409, 503).
- **CSV**: Apache Commons CSV for reading and writing.
- **Exchange rates**: `RestClient` against the Kurs API, reading `exchange_middle / parity`.
- **Scheduling**: a scheduled job on working days.
- **Reports**: `GROUP BY` and `SUM` in the repository layer, returned as projections.
- **API documentation**: springdoc-openapi, serving both Scalar and Swagger UI.
- **Testing**: pure JUnit for parsing, fingerprinting, rule selection and conversion arithmetic;
  Testcontainers with a shared container for everything query-shaped; Kurs mocked at the HTTP
  boundary.
- **Docker**: one `compose.yaml` with `postgres` and `app`; `docker compose up` runs everything.
- **CI**: GitHub Actions building and testing on every push.
- **README**: description, how to run, endpoints, the sample CSV, and screenshots.

## Roadmap

Seven steps. Step 7 of the original was two things — user accounts *or* a dashboard frontend —
and they are answered separately. The frontend was built, as step 7 below; user accounts were
not, and are not planned. The original reason for dropping both was that a sibling portfolio
project already demonstrates JWT and a React client, which remains the reason there is no
authentication here, but was never a reason for the pipeline to have no way of being used
except `curl`.

1. **Setup** — project config, compose, first migration, seed data, CRUD for categories and
   transactions
2. **CSV import** — upload, parsing, validation, duplicates, summary, error history
3. **Categorization** — rules, matching during import, re-apply
4. **Exchange rates** — Kurs client, scheduled job, on-demand backfill, conversion
5. **Reports** — monthly, comparison, top expenses, CSV export
6. **Quality** — tests, Dockerfile, GitHub Actions, README
7. **Frontend** — a React client over all 23 endpoints, built by Maven and served from the jar
   ([ADR-0008](../../docs/adr/0008-the-client-is-built-by-maven-and-served-from-the-jar.md))

## Deviations from the original specification

| # | Change | Why |
| --- | --- | --- |
| 1 | Spring Boot 4.2.0, not implied 3.x | ADR-0001 |
| 2 | No `type` column; sign is the source of truth; zero rejected everywhere | ADR-0002 |
| 3 | Expenses reported as negative; positive change means better off | ADR-0002 |
| 4 | `fingerprint` nullable, null for manual rows, frozen on edit | ADR-0003 |
| 5 | Duplicates detected within a single file, not only against the database | ADR-0003. The original's own sample file would otherwise roll back the whole import |
| 6 | Category type filters rule candidates before priority ranks them | ADR-0004 |
| 7 | Missing historical rates fetched on demand, not only by the scheduled job | ADR-0005. Foreign lines in any historical statement were otherwise always rejected |
| 8 | Rates resolved before the transaction opens, never inside it | ADR-0005 |
| 9 | Transport failure gives 503 and no write; unpublished currency rejects one line | ADR-0005 |
| 10 | Testcontainers-weighted suite, Kurs mocked at the HTTP boundary | ADR-0006 |
| 11 | Uncategorized appears as a null-category row in reports | Category totals must sum to the balance |
| 12 | Comparison covers categories in either month | A category that stopped is the most informative line the report has |
| 13 | Export is RFC 4180 with a BOM, category rows only | One CSV dialect per project; a summary block makes the file unreadable by tools |
| 14 | `spring-boot-docker-compose` removed, `app` service added | `docker compose up` must run the app, not just the database |
| 15 | Seeded categories and rules ship in migrations | The categorization feature is otherwise invisible on first run |
| 16 | Both Scalar and Swagger UI served | Verified non-conflicting |
| 17 | ~~Step 7 dropped~~ — **superseded.** Only its user-accounts half is dropped; the frontend was built in step 7 | The sibling project covers JWT, which is why there is still no authentication. It does not cover this pipeline, which had no interface but `curl` |
| 18 | The client is built by Maven and served from the application's own jar | ADR-0008. One artifact and one origin, so there is no CORS configuration and no second service to start |
