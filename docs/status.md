# Status

**Last update:** end of phase 9. The planned MVP is complete: every phase from the foundation to deployment is built, tested and documented. This file is the honest account of what exists, how it is proven, and what is left.

## Phases

| Phase | Scope | Status |
|---|---|---|
| 0 | Requirements and architecture decisions | Done |
| 1 | Foundation: skeleton, CI, local environment | Done |
| 2 | Identity and authentication | Done |
| 3 | Organizations and tenant isolation | Done |
| 4 | Memberships, invitations, roles | Done |
| 5 | Departments and service requests | Done |
| 6 | Approval workflow and audit history | Done |
| 7 | Dashboard, request assignment, accessibility | Done |
| 8 | Security hardening, platform administration, logging and failure behavior | Done |
| 9 | Deployment, demonstration, end-to-end tests, documentation | Done |

## Acceptance criteria

| Criterion | Status | Evidence |
|---|---|---|
| People can register, verify their email, sign in and sign out securely | Done | Enumeration-safe registration, hashed single-use tokens, session cookies with CSRF protection, account re-checked on every request; backend tests and an end-to-end journey with the real email |
| People can create organizations and belong to several | Done | Organization switcher; a demo person belongs to two |
| Owners and Admins can invite people, change roles and remove members | Done | Role-assignment policy, last-owner protection, locking against races; backend and component tests |
| Permissions are enforced on the server for every action | Done | One permission matrix and access policy; each endpoint tested for each role; the UI only shows hints |
| Cross-tenant read and write attempts are tested and rejected | Done | Isolation battery on every tenant endpoint, route-inventory test, foreign-id tests, raw-SQL tests of every composite foreign key, browser tests with a person in two organizations |
| Departments can be managed and members assigned | Done | Unique names per organization, deactivation instead of deletion, assignment protected by composite keys |
| People can create, edit, submit, review, approve, reject and track internal service requests | Done | Full lifecycle with comments, history, version checks, the self-approval rule and race tests; browser test through four people |
| Reviewers can assign requests and see their own queue | Done | Advisory assignment with history and audit; "assigned to you" on the dashboard and as a filter |
| Important actions are auditable | Done | Events recorded in the business transaction; append-only table; Owners and Admins read them; no personal data or comments in the log |
| Every member has a dashboard consistent with the lists | Done | Counts follow the same visibility rules as the request list; a test compares every number with the matching list total |
| A platform level exists that never sees tenant data | Done | Configuration-declared administrators, metadata-only catalog, suspension with fixed reasons, platform audit view |
| The frontend handles validation, loading, errors and conflicts | Done | Component tests for every state; conflict explanations survive the panel that triggered them; outage handling |
| The interface is accessible and responsive | Done for what is automated | Axe scan of every main screen (WCAG 2.0 to 2.2 A and AA), keyboard, focus and reflow tests; no screen-reader or user testing |
| Secure by default over HTTP | Done | Response headers, cookie flags, size limits, architecture rules, dependency scanning |
| Failures are handled safely | Done | Safe logging policy, incident references, `503` with `Retry-After` during outages, graceful shutdown |
| Reproducible setup and deployable containers | Done | Compose for development and for the deployable stack; non-root read-only images; smoke test |
| Automated gates in CI | Done | Tests, security scan, deployment check with end-to-end tests, docs check |
| Documented | Done | This documentation set, 36 decision records, generated route reference |

## Negative tests (things that must not work)

| Attempt | Result | Proven by |
|---|---|---|
| Read or edit another organization's request, department, member or audit log | `404`, identical to a missing resource | Isolation battery, foreign-id tests, browser test |
| Reach any `/api/orgs/{id}/...` route as a non-member | `404` | Route inventory |
| Reach any `/api/platform/...` route as an ordinary user, owner or anonymous caller | `404` (anonymous: `401`), identical body | Platform gate tests, route inventory |
| Use the platform role to read tenant data | `404` as for any non-member | Platform tests |
| Cross-tenant reference written directly to the database | Rejected by a foreign key | Schema tests for each composite key |
| Employee sees a colleague's request, even by its address | `404` | Backend and browser tests |
| Employee reviews or assigns; manager reads the audit log | `403` | Role tests, browser tests |
| Review your own request, whatever your role | `403` | Workflow tests, browser tests |
| Decide on a request that changed since you opened it | `409` | Version tests |
| Two reviewers decide at the same time | One wins, the other `409` | Parallel tests, browser test |
| Removed or demoted member keeps acting | Refused on the next request; open assignments cleared | Member and assignment tests |
| Suspended organization keeps working | `403 ORGANIZATION_SUSPENDED` at once; invitations cannot be accepted | Platform tests |
| State-changing call without the CSRF token | `403` | Security tests |
| Oversized body, declared or chunked | `413` before parsing | Filter and integration tests |
| Reuse or forge a verification or invitation token | Refused; tokens are hashed and single-use | Identity and invitation tests |
| Search text used as SQL or as wildcards | Treated as text | Search tests |
| Markup in titles, names or comments | Rendered as text | Component tests |
| Rewrite request history or the audit log | Rejected by database triggers | Schema tests |
| Database outage | `503` with `Retry-After`, never a sign-out; recovers without restart | Outage tests, component test |
| Personal data or tokens in logs | None, including real database errors | Logging-policy tests |
| Reach internals through the deployed proxy (actuator, database, API docs) | `404` or not published | Smoke test, configuration tests |

## Modules

| Module | State |
|---|---|
| A. Identity and authentication | Complete (no password reset, email change or multi-factor authentication) |
| B. Organizations, memberships and invitations | Complete |
| C. Departments | Complete |
| D. Service requests | Complete (no withdraw, cancel or delete of drafts) |
| E. Approval workflow | Complete |
| F. Audit history | Complete (no export or actor filter) |
| G. Dashboard | Complete |
| H. Platform administration | Complete (configuration-based administrator list, no multi-factor authentication) |
| I. Deployment and operations | Complete as a deployable stack and demo; backups, image scanning and observability are not built |

## Remaining work and technical debt

Grouped, roughly by how much I would want it before a real production use.

**Before any real deployment**
1. Confirm `backend/pom.xml` uses a stable Spring Boot release. The project moved to a milestone to obtain fixed Tomcat and Jackson versions; if the parent still says `-M`, return to the latest stable release with explicit overrides for the vulnerable libraries and re-run the scan.
2. Review the dated exceptions in `frontend/osv-scanner.toml` (the first expires on 2026-12-31).
3. Automated database backups and a tested restore.
4. Image vulnerability scanning in CI.
5. A global timeout for JPA repository queries (`JdbcClient` queries already have one); verify the right Hibernate setting first.
6. A test that proves the per-IP rate limits see the real client address behind the proxy (forwarded headers `native`).
7. Log aggregation and alerting; make incident ids searchable.
8. Tighten the SPA's `style-src` once the browser tests show nothing needs inline styles.

**Product gaps**
9. Password reset, email change, multi-factor authentication, account deletion and data export.
10. Withdraw and cancel for requests, deleting drafts, an organization setting for self-approval, history pagination.
11. Audit export, filter by actor, retention tooling.
12. Platform administrators: multi-factor authentication, rate limits for platform actions, changing the list without a restart.
13. Distributed rate limiting (needed for more than one instance).

**Quality and maintenance**
14. URL-persisted filters for the request and department lists (shareable links).
15. A searchable member and reviewer picker (both cap at 100).
16. Consolidate duplicated helpers: LIKE escaping (`DepartmentSearch`, `RequestQueries` should use `LikeEscaper`), audit-metadata expansion (tenant and platform viewers), the members page's own pagination and control-character check, UTC day-range conversion (request and audit services).
17. A behavioral test of the mail-failure path.
18. Recent-activity query for employees filters by creator after a time-ordered scan; measure before optimizing.
19. Audit other components that hold feedback and can unmount (invitation and member panels) and add a conflict test for each.
20. More accessibility depth: screen-reader and user testing, richer page titles, a scan of any dark theme, other browser engines in the end-to-end suite.
21. Refresh the screenshots after UI changes (`npm run capture` in `e2e`).
22. The sign-in check does not retry automatically after an outage (the person presses "Try again").
