# 06 — Quality: tests, Dockerfile, CI, README

Status: resolved
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

## Comments

Done. The audit of rules 1 to 16 found four without a test that would fail if the rule broke,
and the four are now written:

- **Rule 9** had only half its coverage. A category a rule points at could not be deleted and was
  tested; a category holding money could not be deleted and was not. Both answer 409, so the new
  test asserts the reason and not only the status — a test reading either half as the other
  would have stayed green while one of them was gone.
- **Rule 10** had no test at all, and could not have had an ordinary one: a `double` in the money
  path is wrong in the seventeenth decimal place, not the second, so no assertion about a report
  would notice it. `MoneyIsExactTest` asserts the type directly in three places — the columns
  through `information_schema`, every declared signature through reflection over the compiled
  application, and the compiled bodies for the four calls that turn an exact number inexact
  (`amount.doubleValue()` leaves every signature a `BigDecimal` and loses the money anyway).
- **Rule 11** was covered for the case where Kurs answers with an earlier `date_from`, but not for
  the case where the rate is already on file: a Sunday line converting at Friday's stored rate
  with no download at all. That is the path a converter looking for an exact date would break,
  and it would break it silently until the day the bank was unreachable.
- **Rule 13** was the real hole. Every report fixture wrote `amount == amount_rsd`, so a report
  summing the wrong column passed every test in the suite. The new fixture's euro line is worth
  117 times its own number, which no rounding error can disguise.

Each of the three code-level ones was mutation-checked rather than merely run: a `double` field
and a `doubleValue()` call were each introduced into a main class, the test was watched to fail
and to name the offender, and the mutation was reverted.

### What CI would have done without a review pass

Two things found by review, both of which would have made "CI is green on a push" false on the
first push:

- `mvnw` was committed `100644`. `./mvnw -B verify` on `ubuntu-latest` is "Permission denied";
  fixed with `git update-index --chmod=+x`.
- The miscompiled-class detector was reported as aborting under `bash -e` on a clean build. It
  does not — the loop's pipeline ends in `sed`, which exits 0, and the default shell on a Linux
  runner is `bash -e` without `pipefail` — but it would abort the moment anyone added
  `shell: bash`, which adds `-o pipefail`. The grep now carries its own `|| true`.

The detector also searches `target/generated-sources` now. ADR-0007 says the corruption is always
in or near the generated metamodel, and metamodel classes have no file under `src/`, so the
canonical snippet was looking everywhere except the likeliest place.

### Deliberate departures

- **The compose smoke test in CI uploads a dinar-only statement, not `sample.csv`.** The sample's
  euro line resolves its rate from the National Bank at import time (ADR-0005), so uploading it
  in CI would make an outage at kurs.resenje.org a red build on a commit that changed nothing.
  The euro path is asserted in the suite, where Kurs is faked at the HTTP boundary (ADR-0006).
  The README says plainly that this one line of the walkthrough needs the internet.
- **`compose.yaml` gained an app healthcheck.** Not in the issue, but the issue's first "Done
  when" clause is false without it: `docker compose up --wait` returned about six seconds before
  Tomcat accepted a connection, so the very next command in the README — and in CI — raced it.
  The probe is `GET /api/categories` through busybox `wget`, which the Alpine JRE image already
  carries; it reads the migrated database, so it passes only when a real request would.
- **The README documents five known limitations, not the three the issue names.** The two extra
  ones are honest and cheap: there is no authentication, and `POST /api/transactions` still
  refuses a foreign currency with a message promising rates "until exchange rates arrive" —
  which arrived in step 4. Only the import path was wired to them. Worth its own ticket; it is a
  stale message and a missing conversion on the manual path, not a quality problem with step 6.

### Verified, not assumed

- Full suite: 163 tests, no failures. The detector finds no miscompiled class file.
- `docker compose down -v` then `up --build --wait` on an empty volume, then `sample.csv` uploaded
  over HTTP: **total 7, imported 4, duplicate 1, rejected 2**, and `GET /api/reports/monthly`
  came back populated with the seeded category names, the euro line among them converted at the
  live National Bank rate for its own date. No manual setup in between, which is the first
  "Done when" clause end to end.
- The CI smoke script was run verbatim against a freshly built stack before being committed.
- `actionlint` is clean on the workflow.
- The screenshots in `docs/images/` are of this application running, captured from the stack
  above.

The second "Done when" clause — CI green on a push — is the one thing that cannot be verified
from here. Everything it runs has been run locally.
