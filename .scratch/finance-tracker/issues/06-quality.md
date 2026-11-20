# 06 — Quality: tests, Dockerfile, CI, README

Status: ready-for-agent
Blocked by: 05

Roadmap step 6. The step that makes it portfolio-ready.

## Scope

- Close the gaps: every business rule in the spec has at least one test. Audit rules 1 to 16
  against the suite and fill what is missing.
- One shared Testcontainers instance for the whole suite, started once (ADR-0006).
- `Dockerfile` — multi-stage, building with Maven and running a slim JRE image; non-root user.
- GitHub Actions: build and run all tests on every push. Docker is available on the runner, which
  the Testcontainers suite requires.
- README: what it is, `docker compose up`, the endpoint table, the sample CSV with its expected
  summary, Scalar and Swagger links, and screenshots.
- README must explain two things that read as bugs otherwise: expenses are negative numbers, and a
  positive percent change means the position improved.
- Document the known limitations in one place: identical same-day payments counting as one,
  refunds staying uncategorized, and re-import after delete restoring a row.

## Done when

- A clean clone, `docker compose up`, upload `sample.csv`, and `GET /api/reports/monthly` produces
  a populated report with real category names — with no manual setup in between.
- CI is green on a push.
