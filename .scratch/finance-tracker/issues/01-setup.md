# 01 — Setup: project config, compose, schema, seed data, CRUD

Status: ready-for-agent

Roadmap step 1. Everything needed for `docker compose up` to produce a running API with a real
schema, reference data, and working CRUD over categories and manual transactions.

## Scope

**Build and run**
- Add dependencies: Apache Commons CSV, springdoc Scalar and Swagger UI starters, Testcontainers
  (postgres, junit-jupiter), `spring-boot-testcontainers`.
- Remove `spring-boot-docker-compose` (ADR-0001 context; see spec deviation 14).
- Rewrite `compose.yaml`: `postgres:18` with a healthcheck, fixed `5432:5432`, a named volume,
  real credentials from `.env`; an `app` service built from a `Dockerfile`, depending on the
  database being healthy.
- Commit `.env.example`; keep `.env` ignored.
- `application.properties`: datasource from environment variables with localhost defaults, Flyway
  on, `ddl-auto: validate`.

**Schema** — `V1__schema.sql`, all six tables per the spec, including:
- `CHECK (amount <> 0)` on transaction
- `CHECK ((source = 'IMPORT') = (fingerprint IS NOT NULL))`
- unique index on `fingerprint`, unique on `(currency, rate_date)`, unique on category `name`
- indexes supporting the transaction filters (date, category) and the rate lookup
  `(currency, rate_date)`

**Seed data** — `V2__seed_categories.sql`, `V3__seed_rules.sql`. English category names, Serbian
keywords. Salary, Food, Utilities, Transport, Entertainment, Health; rules MAXI, LIDL, EPS,
BUS PLUS, NETFLIX, PLATA.

**CRUD**
- Entities, repositories, request/response records, services, controllers for Category and
  Transaction.
- Transaction type derived from the sign of the amount; never stored (ADR-0002).
- Manual transactions get `source = MANUAL` and a null fingerprint (ADR-0003). For an RSD
  transaction `amount_rsd` equals `amount`; a manual transaction in another currency is out of
  scope for this step and may be rejected with 400.
- Category delete returns 409 when transactions or rules reference it.
- Transaction list: filters, paging (default 20, max 100), sort default `date DESC, id DESC`.
- One `@RestControllerAdvice` producing consistent JSON for 400, 404 and 409.

## Done when

- `docker compose up` starts the database and the app, migrations apply, Scalar and Swagger UI
  both render the API.
- A Testcontainers test asserts the schema rejects a zero amount, rejects an `IMPORT` row with a
  null fingerprint, rejects a `MANUAL` row with a fingerprint, and permits two manual rows that
  would otherwise collide.
- Controller tests cover the category 409 and the transaction filter and paging defaults.
