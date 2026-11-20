# Query-shaped logic is tested against a real PostgreSQL, not a mocked repository

The specification asked for JUnit and Mockito over the import, categorization and conversion
logic, and at least one Testcontainers integration test. The weight is distributed the other
way round. Pure JUnit covers what is genuinely pure — parsing and validating a line, computing
a fingerprint, selecting among matching rules, the conversion arithmetic. Everything else runs
against a real PostgreSQL in a container shared across the whole suite.

This is not a preference for integration tests. It is that almost every decision this project
makes lives in a query or a constraint. Whether duplicates are found with one set-based lookup.
Whether the rate lookup picks Friday's rate for a Sunday. Whether two null fingerprints can
coexist under a unique index. Whether the monthly breakdown keeps the rows with no category.
Whether the month-on-month comparison is a full outer join and so can report a category that
stopped. A service test with a mocked repository asserts that the service called a method the
same author wrote, and can observe none of these. A suite built that way would have been green
throughout and would have caught none of the defects found while designing it.

Running against the real database has a second effect worth having: the Flyway migrations are
exercised on every test run, so a broken migration fails the build rather than failing
`docker compose up` later.

The exchange-rate API is faked at the HTTP boundary rather than behind a Java interface,
because the distinction the import depends on — an unpublished currency is the line's fault, a
transport failure is not — is a distinction between HTTP outcomes. A stub programmed to throw
proves only that a stub can be programmed to throw. Binding a mock server to the client lets
the test serve a genuine 503, a genuine timeout and a genuine malformed body.

## Consequences

The container starts once per suite rather than once per class, which keeps the cost bearable,
but the suite still needs a working Docker daemon — locally and in GitHub Actions. There is no
in-memory fallback, and adding one would defeat the reason the tests exist.
