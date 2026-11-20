# 03 — Categorization: rules, matching during import, re-apply

Status: ready-for-agent
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
