# 03 — Categorization: rules, matching during import, re-apply

Status: resolved
Blocked by: 02

Roadmap step 3.

## Scope

- CRUD for `CategoryRule` at `/api/rules`.
- Matching: description contains keyword, case-insensitive.
- **Candidate filtering before ranking** (ADR-0004): only rules whose category type matches the
  transaction's type — derived from the sign of the amount — are candidates. Highest priority wins
  among those. No compatible candidate means the transaction stays uncategorized.
- Hook matching into the import pipeline, replacing the null-category seam from 02.
- `POST /api/rules/apply` re-runs matching over uncategorized transactions only, and reports how
  many were categorized.
- `PATCH /api/transactions/{id}/category` enforces the same type rule, returning 409 on a mismatch.

## Done when

- A unit test covers the refund case from ADR-0004: an `EPS` rule pointing at Utilities (expense),
  a positive `EPS SNABDEVANJE POVRACAJ` transaction, and the assertion that it stays uncategorized
  rather than taking the expense category.
- A unit test covers a high-priority incompatible rule losing to a lower-priority compatible one.
- Importing `sample.csv` with the seeded rules categorizes PLATA as Salary, MAXI as Food, EPS as
  Utilities and BUS PLUS as Transport, leaving nothing uncategorized — so the uncategorized path
  needs its own test with a description no seeded keyword matches.

## Comments

Done. 92 tests green against a real PostgreSQL 18, and the endpoints were also driven against a
running app, which is how the two worst things here were found: a `@Transactional` test keeps a
session open around the request and hides anything that only breaks once the write has committed.

Matching is `CategoryRule.appliesTo` for candidacy and `Categorization` for ranking, which is
ADR-0004 split the way the ADR splits it: a rule whose category cannot legally take the
transaction is not a candidate, so the type check is a filter over a priority-ordered list rather
than a veto applied to its answer. `CategorizationTest` states the refund case and the case the
ADR was written to settle — an `EPS`/Utilities rule at priority 100 against a `POVRACAJ`/Refunds
rule at 10, on income, answering Refunds. Ranking first and vetoing afterwards answers nothing
there, so that test fails against the design the ADR rejected.

Three things were worth more than the line of code they cost:

- **Categorizing the imports broke every listing, in the real application only.** `Transaction`
  maps its category lazily and `spring.jpa.open-in-view=false`, so the session is gone by the
  time `TransactionResponse` reads the category name. It had never mattered: imported rows all
  arrived uncategorized, and a manually categorized row carried a category the request had just
  loaded. Filling categories in at import turned that into a `LazyInitializationException` on
  `GET /api/transactions` for every page with a categorized row — a 500 that the whole test suite
  was green through. Fixed with an `@EntityGraph` on the two read methods, which also makes a
  listing one query instead of one per distinct category on the page. `CategorizedListingTest` is
  deliberately not `@Transactional`, for the same reason `ImportWriterTest` is not: it commits,
  then reads in a request of its own, and it fails without the graph.
- **The build still miscompiles, and `incrementalCompilation` was not the cure.** The note on 02
  diagnosed this as the incremental check dropping a class. With that off, javac now writes class
  files whose constant pool names a class without its package — `LCategoryRepository;` instead of
  `Lcom/igorpujovic/finance_tracker/category/CategoryRepository;` — in the classes near the
  generated `*_` metamodel. The build reports success; the failure lands at class loading, as a
  `NoClassDefFoundError` for a class sitting in `target/classes`. Measured on this machine, over
  eight clean builds of untouched `9fbf401`: five corrupt. It is not the plugin version (3.14.0
  behaves the same), not `-XDuseUnsharedTable`, and forking javac only drops it to 1 in 12; a
  two-pass `proc:only` then `proc:none` compile is the usual cure and needs the generated sources
  wired in by hand. Left alone deliberately — it predates this step, it is not categorization,
  and guessing at it inside this issue would bury a real build problem in a feature commit. It
  wants its own issue. Until then a build that fails this way is fixed by running it again.
  **Since resolved as 07**, which found two causes rather than one: see ADR-0007.
- **One order, used twice.** `GET /api/rules` and the matcher ranked rules differently at first
  — `priority desc, keyword asc` against `priority desc, id asc` — so the list could show one
  rule first and a tie could be won by another. They now share all three keys, so the rule the
  list puts first is the rule that wins, and a tie resolves the same way on every run. Without
  that last part the same statement imported twice could categorize differently.

Four things beyond the letter of the scope. `POST /api/rules/apply` reports `examined` as well as
`categorized`, because the difference is the queue still waiting on a person. `RuleResponse`
carries the category's type, since that is half of whether a rule can ever fire. The compatibility
rule of ADR-0004 moved to `TransactionType.canTake`, which deletes the hand-written version in
`TransactionService` — it is now stated once and read by both the rules and the `PATCH` endpoint.
And the `PATCH .../category` 409 already existed from 01 but was only tested through `POST`, so it
now has a test of its own that also checks the category it refused to change is still there.

One deviation from **Done when**, agreed with the author before starting. It asks that importing
`sample.csv` categorize BUS PLUS as Transport, but that line is dated `31.11.2026` and the strict
parser rejects it, so it never becomes a transaction — the fixture's own invalid-date case is that
line. The assertions over `sample.csv` cover what it really imports (PLATA, MAXI, EPS, and nothing
left uncategorized, with the row count pinned so a dropped row cannot pass as success), and
`BUS PLUS` → Transport has its own one-line statement dated `30.11.2026`.
