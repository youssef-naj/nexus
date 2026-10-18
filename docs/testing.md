# Testing

Each layer proves something the others cannot. Details of the decisions are in [ADR-0036](decisions/0036-end-to-end-and-accessibility-testing.md).

| Layer | Where | What it proves | Run |
|---|---|---|---|
| Backend unit and integration | `backend/src/test` (about 410 tests) | Rules, endpoints, concurrency and database constraints against **real PostgreSQL** (Testcontainers) | `cd backend; .\mvnw.cmd verify` |
| Architecture rules | `ArchitectureTest` | Module boundaries, layering, tenant-scoped repositories, logging rules | part of `verify` |
| Frontend component | `frontend/src/**/*.test.*` | Every screen's states (loading, empty, error, conflict), form validation, safe rendering, permission-driven controls | `cd frontend; npm run test` |
| Static checks | frontend and backend | Formatting, types, lint | `npm run format; npm run typecheck; npm run lint`; `spotless:check` in `verify` |
| Route reference | `ApiRouteDocumentationTest` | `docs/api-routes.md` matches the code | part of `verify` |
| Smoke test | `scripts/smoke.sh` | The built stack serves the site, the headers and one real user journey | `bash scripts/smoke.sh` |
| End-to-end | `e2e/tests` (43 tests) | Real journeys and access rules in a browser against the production images | `cd e2e; npx playwright test` |
| Accessibility | `e2e/tests/accessibility.spec.ts` | WCAG 2.0 to 2.2 A and AA on every main screen (axe), desktop and phone | `npm run test:a11y` in `e2e` |
| Documentation | `scripts/check-docs.mjs` | Links resolve, the decision index is complete and numbered without gaps | `node scripts/check-docs.mjs` |

## Conventions

- **A new tenant endpoint** must be added to the isolation battery (`CrossTenantAssertions.assertIsolated`); the route-inventory test fails when a tenant route is reachable by a non-member. A new `/api/platform` route is covered by the platform inventory test.
- **A new table that references tenant data** gets composite foreign keys and a schema test that inserts a cross-tenant row with raw SQL.
- **Concurrency rules** get a parallel test (`Parallel.statuses`) that provokes the race.
- **Front-end states:** every screen has tests for loading, empty, error and, where it applies, conflict. Wait for data-only text, then query inside the table or region, because filters and tables share words.
- **Logging:** anything that handles personal data gets a case in the logging-policy test.
- **End-to-end tests** create uniquely named data and never edit the seeded story. Tests that change shared state clean up through the API, not through the page.

## End-to-end tests

They need the seeded demo (see [demo.md](demo.md)). A global setup signs each demo account in through the API and saves its session under `e2e/.auth/` (git-ignored); multi-person tests open one browser context per person.

```powershell
cd e2e
npm install
npx playwright install chromium
npx playwright test                 # all
npm run test:a11y                   # accessibility only
npx playwright show-trace test-results\<folder>\trace.zip   # after a failure
```

If the platform tests are skipped, restart the backend once after seeding so the operator role is granted.

## CI

Every push runs: **CI** (backend `verify`, frontend checks), **Security scan** (dependencies, also weekly), **Deployment check** (builds the images, starts the stack, smoke test, end-to-end and accessibility tests; the Playwright report is kept when something fails), and **Docs check**.

## Not covered

Screen-reader and user testing, other browser engines, load testing, penetration testing, restoring from a backup.
