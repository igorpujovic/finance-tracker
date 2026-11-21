# 09 — The pipeline has no interface but curl

Status: resolved

Roadmap step 7, which the spec dropped and deviation #17 recorded as dropped. Reopened
deliberately rather than quietly: the original reason covered two things at once — user accounts
*and* a dashboard frontend — and only one of them was really answered by "the sibling project
already demonstrates it". JWT is genuinely covered there. The absence of any way to use this
pipeline other than `curl` was not a conclusion, it was the same sentence carrying a second
clause along with it.

## What was wrong

Six steps produced 23 endpoints, 174 tests, two API documentation viewers and a README whose
walkthrough is two `curl` invocations. Everything the project argues about — that a rejected line
is kept verbatim, that an expense is negative and nothing else marks it, that a positive change
means the position improved — was legible only to somebody reading JSON.

Four places in the repository stated there was no client, and one of them was about to become
false:

- `spec.md`, the roadmap paragraph: "Step 7 ... is out of scope".
- `spec.md`, deviation #17: "Step 7 dropped".
- `README.md`, under Known limitations: "the sibling portfolio project already argues JWT and a
  React client".
- `README.md` throughout, which describes the project as "A REST API that…".

## Done

React 19 + TypeScript + Vite, in `frontend/`, covering **all 23 endpoints** — the coverage was the
point, so that nothing is left reachable only by `curl`.

### The two decisions, both in ADR-0008

**One artifact, one origin.** Maven builds the client into the jar's static resources and Spring
serves it at `/` on the same port as the API. No second service, no CORS configuration, and
`docker compose up` is still the whole of the setup. The export endpoint gets to be a plain
`<a href>`, so the browser keeps the `Content-Disposition` and the byte-order mark that a blob
round-trip through JavaScript would lose.

**Maven builds it, not the Dockerfile.** The cheap version — a Node stage in the `Dockerfile` —
works for `docker compose` and fails everywhere else: CI runs `./mvnw verify`, so the client
would never be compiled by CI at all, and the same commit would produce one jar with a UI and one
without. `frontend-maven-plugin` binds to `generate-resources`, which sits between the two
compiler executions of ADR-0007 and forks no `javac`.

### What the client had to get right

Each of these is a documented convention of this project, and each has a test:

- **The sign is never removed.** There is no `Math.abs` anywhere in the client except on a bar's
  *length*, which cannot be negative — and the figure printed on that bar is still the signed one.
- **A positive change is an improvement**, on both sides of the ledger. Food at `+2.549,20` /
  `+50,98%` is labelled *less went out*; a salary that stopped is `−100%` and *stopped coming in*,
  while a bus pass that stopped is `+100%` and *stopped going out*. A "reads as" column spells it
  out, because that figure is the one a reader is most likely to get backwards.
- **`percent: null` is `—`**, never `0%` and never infinity.
- **The uncategorized row is rendered**, with its money, in reports and in the comparison — it is
  a real state, and the category totals have to visibly sum to the balance.
- **Rejected lines are shown verbatim**, in a monospace block that is not re-wrapped or trimmed.
- **503 is not the reader's mistake.** The error renderer tells the National Bank being
  unreachable apart from a validation failure and from a conflict, and says nothing was saved.

### Two things found by running it, which no amount of typechecking would have caught

**The chart was useless on real data.** It was first written as one zero-baseline bar chart with
income right of zero and expenses left — defensible on paper, and the way the two kinds are told
apart by position rather than by colour. On the sample month a 95.000 salary flattened every
expense into a two-pixel sliver. It is now two panels, each scaled to its own largest bar. The
position-based separation survives as the panel split, which is what the measured colour
separation of the green/red pair needs.

**A cached `index.html` is a blank page after every deploy.** Spring sends no cache headers for
static resources, Chrome's heuristic is to cache, and `index.html` is the one file with a fixed
name that names the hashed ones — so a returning browser asks for an asset that is no longer in
the jar. This happened twice during the work and was mistaken for a broken build both times.
`StaticResources` now sets `no-store` on `index.html` and a year plus `immutable` on `/assets/*`,
and `SpaRoutes` maps `/` as well so the root gets the same headers as a reloaded client route.

### Colour was computed, not chosen

The two money colours were put through the palette validator rather than picked by eye, which
caught two things. The first green/red pair failed colour-vision separation outright. And the
colour that is correct for a chart *mark* (3:1 against the surface) is not correct for *text*
(4.5:1): the chart red measures 3.95:1 as light-mode text. There are therefore two pairs — marks
and figures — and the file says why. CVD separation for the mark pair is ΔE 7.2 light / 7.1 dark,
which is in the band that is legal only with a second, non-colour encoding; the client has three
(panel position, a printed sign on every figure, and a direct label on every bar).

### Verified, not assumed

- **`./mvnw verify` is green: 174 Java tests and 47 client tests**, from one command. The client's
  build typechecks before it bundles, so a type error fails the Maven build.
- The client's 47 are not padding: they pin the sign conventions, `percent: null`, the
  income-stopped/spending-stopped distinction, the uncategorized row, the four API error statuses
  told apart, and that an upload lets the browser set its own multipart boundary.
- **Driven in a real browser against the packaged jar**, not only in jsdom. `sample.csv` uploaded
  through the UI gives the summary the README pins — **7 lines, 4 imported, 1 duplicate,
  2 rejected** — with both rejected lines shown verbatim and their reasons. The dashboard for
  2026-11 reads `95.000,00` / `−8.495,64` / `86.504,36`, the Netflix line converted at the live
  National Bank rate to `−1.174,84`. Console clean: no errors, no React warnings.
- `/` and `/transactions` both serve the shell with `no-store`; `/assets/*` is `immutable`;
  `/api/categories` still answers `application/json` and `/scalar` still answers 200 — the SPA
  forward is not shadowing anything.

### The four places

All four now say the same thing. Deviation #17 is marked superseded rather than deleted, because
half of it still holds and the reasoning is worth keeping: the sibling project covers JWT, which
is why there is still no authentication here. A new deviation #18 records ADR-0008. The README's
limitation keeps "one user, no accounts, no authentication" and drops only the clause about a
React client, which is now false.

## Comments

A note for whoever reads the tracker next, repeating one from 08: `resolved` is not among the five
labels in `docs/agents/triage-labels.md`, but all nine issues here use it. The doc is stale, not
these files.
