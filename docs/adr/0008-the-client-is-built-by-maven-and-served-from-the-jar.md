# The client is built by Maven and served from the jar

The React client lives in `frontend/`, is compiled by the Maven build, and is served by the same
Spring Boot application that serves the API, from the same port. There is one artifact, one
process, and no CORS configuration anywhere in this project.

The alternatives were a second container serving the built client behind nginx, and a Node stage
in the `Dockerfile` that builds it into the image without Maven knowing. Both were rejected, for
different reasons.

## Why not a second service

A separate origin for the client means the API has to permit it, which means a CORS
configuration: a list of allowed origins that is right in development, right in compose, and
wrong the first time anybody deploys it anywhere else. It also means two things to start and two
ports to explain, against a README whose first promise is that `docker compose up` is the whole
of the setup. The one real benefit — scaling the static files independently of the API — is not a
benefit to a single person's ledger.

Serving both from one origin removes the question rather than answering it. The client fetches
relative paths (`/api/...`), which are correct in the jar without configuration, and correct
under `npm run dev` because the Vite dev server proxies that prefix. Nothing in the client knows
where the API is, so nothing in the client can be pointed at the wrong one.

The same choice pays for itself twice more. `GET /api/reports/monthly/export` can be a plain
`<a href>` rather than a fetch, so the browser honours the `Content-Disposition` and keeps the
byte-order mark that makes the export open correctly in Excel — both of which a blob round-trip
through JavaScript tends to lose. And there is no authentication to forward across an origin,
which there would eventually have to be.

## Why Maven builds it, and not the Dockerfile

This is the part that is easy to get wrong in the cheap direction. A Node stage in the
`Dockerfile` is less configuration than `frontend-maven-plugin`, and it works — for
`docker compose up`.

It fails everywhere else. CI's first job is `./mvnw verify`, and the `Dockerfile` has no part in
it: under a Docker-only client build, CI would never once compile the client, and a TypeScript
error would reach `main` and be discovered by the `docker compose` job at best, or by a reader at
worst. Worse, `./mvnw package` and `docker compose build` would produce *different jars from the
same commit* — one with a UI and one without — which is the kind of difference that is invisible
until it matters.

So the build is defined once, in the pom, and the `Dockerfile` copies `frontend/` and runs the
same Maven build everything else runs. Node is downloaded by the plugin rather than required of
the machine, so the prerequisites are what they were before the client existed: Docker alone, or
a JDK.

Three details of that wiring are deliberate:

- **It binds to `generate-resources`.** That phase sits between the two compiler executions of
  [ADR-0007](0007-the-metamodel-is-generated-by-one-compilation-and-consumed-by-another.md) and
  forks no `javac`, so nothing here touches the arrangement that keeps the metamodel sound.
- **`npm ci`, not `npm install`.** It installs exactly what `package-lock.json` pins and fails
  when the lockfile and `package.json` disagree, rather than quietly resolving something else.
- **Node installs to `frontend/node`, not under `target/`.** `mvn clean` would otherwise discard
  a 30 MB download that no source change can invalidate.

`./mvnw test -P backend-only` skips the client entirely for backend work. It is one switch rather
than two properties because skipping the install without also skipping the test run would leave
`npm test` to be invoked with no `node_modules` to run it.

## The demo is a third build, and says so

There is a published demo at `igorpujovic.github.io/finance-tracker`, and it is not this
application running. It cannot be: the application needs a JVM and a PostgreSQL, and the free way
to host a repository's page serves static files only. Standing up a real instance was considered
and rejected on its own terms — there is no authentication here by design, so a public instance
would be an open ledger anyone could write to, and the free database tiers that would host it
expire or sleep.

What is published instead is the real client with the network replaced: the same components and
the same hooks, with MSW answering `/api` from an in-memory store in `frontend/src/demo`. A
visitor can import a statement, correct a transaction and re-categorize it, and see the real
refusals — a 409 for deleting a category that still holds money, a 400 naming an unpriced
currency.

Its November is `sample.csv`, so every category total is the one the README prints — Utilities
−4.870,00, Food −2.450,80, Entertainment −1.174,84, Salary 95.000,00 — and the expense total is
the README's −8.495,64 exactly. Income and balance are deliberately not: the demo adds an
October month so the comparison report has something to compare against, and a refund that no
rule can claim so the uncategorized row has money in it. Both are things the README's two-curl
walkthrough does not produce and a reader of a dashboard should see.

Two things make this honest rather than a trap. A permanent, undismissable banner says what it is
and that nothing is saved, because somebody will otherwise import a real statement and wonder
where it went. And the fake is a liability worth naming: it is a second implementation of rules
whose truth is the Java, and two copies can disagree. It is kept in check by seeding it from the
same figures the suite asserts, so drift shows up as a demo that stops matching the documentation.

The demo build is a separate mode, not a separate codebase. `npm run build:demo` sets the base
path to `/finance-tracker/`, switches on the in-browser API, and publishes the service worker;
the jar's build does none of those, and the branch that imports the fixtures is behind a flag it
never sets, so the bundler drops MSW and the seed data out of the shipped bundle entirely. The
workflow asserts all three are present in the demo bundle before it publishes, because a mode
flag that silently failed to apply would deploy a client whose every request 404s.

## What serving a single-page client from Spring costs

Two things need saying, and both are small but not optional.

**Client routes have to be forwarded.** Navigating from `/` to `/transactions` happens in the
browser and never reaches the server, but *reloading* `/transactions` does, and there is no such
resource. `SpaRoutes` forwards the client's routes to `index.html`. It names them one by one
rather than catching `/**`, because this application also serves `/api/**`, `/scalar`,
`/swagger-ui.html`, `/swagger-ui/**` and `/v3/api-docs`, and a catch-all competes with all of
them — the symptom being an API that starts answering with HTML. A route added to the client and
not to that list works until someone reloads the page on it, so the two lists are changed
together and each says so.

**`index.html` must not be cached, and the assets must be.** Vite puts a content hash in every
asset filename, so `/assets/*` can be cached for a year and a new build is a cache miss by
construction. `index.html` is the one file with a fixed name and it is the file that names the
hashed ones — cached, it goes on pointing at assets that are no longer in the jar. The symptom is
a blank page for returning visitors only, after a deployment that tested perfectly in a fresh
browser. Spring sends no cache headers for static resources by default, which leaves it to each
browser's heuristics, and Chrome's is to cache; `StaticResources` therefore sets `no-store` on
`index.html` and `immutable` on the assets. This was not a theoretical risk — it happened twice
while this client was being built, and was mistaken for a broken build both times.
