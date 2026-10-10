# MVP status

Checked against the original acceptance criteria. Updated at the end of each phase. Last update: end of Phase 7 (dashboard, request assignment, accessibility and responsive pass).

## Acceptance criteria

| Criterion | Status | Evidence |
|---|---|---|
| A developer can start the application from a clean checkout using documented instructions | Done | README quick start, tested from a fresh clone |
| The database can be created and migrated reproducibly | Done | Flyway V1 to V6, Testcontainers runs every migration on a fresh PostgreSQL |
| Users can create, submit, review, approve, reject and track internal service requests | Done | Full lifecycle in the API and screens: submit, approve, reject, request changes, history with comments, self-approval rule, concurrency tests |
| Cross-tenant read and write attempts are tested and rejected | Done for everything built so far | Isolation battery on organizations, members, invitations, departments, assignments and requests; route-inventory test; foreign-id tests; raw-SQL tests of every composite foreign key | Users can create, submit, review, approve, reject and track internal service requests | Not yet | Phases 5 and 6 |
| Important actions are auditable | Done | Events recorded in the business transaction for registration confirmation, organizations, invitations, members, departments and the whole request workflow; Owners and Admins can read them in the Audit screen | The React frontend handles validation, loading and errors | Done for existing screens | Component tests for each state on every screen; page titles per route, focus moved to the main region after navigation, skip link, labelled scrollable tables, wrapping header and navigation (checked by component tests and manually; no automated browser tests yet) |
| Backend and frontend checks run in CI | Done | GitHub Actions: build, test, format, lint, audit |
| API documentation and architecture diagrams are available | Partly | Architecture and ER diagrams done; OpenAPI is generated but its exposure policy is not decided |
| The project has repeatable demo data and a clear demonstration path | Not yet | Seed data and demo accounts planned |
| Known limitations and remaining security work are documented honestly | Done | docs/security.md |

## Negative tests from the original specification

| Test | Status |
|---|---|
| User A cannot read Organization B's request | Done: request detail and list are tested for outsiders, foreign ids and other members of the same organization |
| User A cannot modify Organization B's request | Done: editing is tested through the foreign organization's path, through its own path, and by non-creators |
| A manager cannot perform owner-only operations | Done: managers and employees cannot invite or change roles; admins cannot touch owners or grant admin |
| An invitation cannot be accepted by an unauthorized account | Done: wrong account, expired, replayed and revoked all fail identically |
| A revoked member cannot continue accessing protected resources | Done: tested for removal, leaving and direct revocation |
| User A cannot download Organization B's files | Not applicable: files are not part of the MVP |

## Modules

| Module | Status |
|---|---|
| A. Identity and authentication | Complete (password reset deferred) |
| B. Organizations and memberships | Complete: create, list, switch, invite, accept or reject, change and revoke roles, leave. Organization settings and rename are not built |
| C. Departments | Complete: create, list, search, update, deactivate, assign and remove members (API and screens) |
| D. Internal service requests | Complete: create, view, edit drafts, filter, search, paginate, submit and review (API and screens) |
| E. Approval workflow | Complete: submit, approve, reject, request changes, history, self-approval rule, concurrency tests, screens |
| F. Audit history | Complete: recording and viewer (Owners and Admins) |
| G. Dashboard | Complete: total, requests by status, awaiting review, assigned to you, my requests and recent activity (API and screen), consistent with the request list |

## Technical debt and open items

Scheduled so nothing is forgotten:

1. **Demo data and demo accounts:** a repeatable seed for local use (Phase 9, possibly earlier).
2. **OpenAPI:** decide per-environment exposure, document error codes, publish the contract (Phase 8 or 9).
3. **Platform administration:** metadata-only endpoints, suspend and reactivate organizations, audited (Phase 8).
4. **Organization settings:** rename and the self-approval policy setting, with audit events.
5. **Audit viewer extras:** export, filter by actor, and a platform-level viewer (with platform administration).
6. **Security headers:** `Referrer-Policy` (tokens appear in page URLs), Content-Security-Policy, explicit `SameSite` for the CSRF cookie (Phase 8).
7. **Architecture tests** (ArchUnit) enforcing the module rules and the "tenant repositories are always scoped" rule (Phase 8).
8. **Playwright** end-to-end tests for the main workflows (Phase 9).
9. **Maven vulnerability scan** in CI (Phase 8).
10. **Deployment:** HTTPS, `Secure` cookies, restricted database runtime role, backups, logs and health checks (Phase 9).
11. **URL-persisted filters** for the request and department lists (shareable links, back button friendly).
12. **Assignee API and the "assigned to me" index** (approval phase).
13. **Cancel or delete drafts** (decide with the workflow).
14. **LIKE escaping is duplicated** in the department and request search classes; consolidate into one tested helper.
15. **Searchable member picker** for department assignment: it loads the first 100 active members today.
16. **Consolidate duplicated frontend helpers:** the members page still has its own pagination controls and control-character check; move it onto `PaginationBar` and `shared/forms/text.ts`, and the organization name schema onto the same text helper.
17. **Assignment follow-ups:** a searchable picker for organizations with more than 100 reviewers, and an "unassigned" queue filter.
18. **Organization policy for self-approval**, with an audit event, if ever wanted.
19. **Withdraw and cancel** for creators (a submitted request currently cannot be taken back).
20. **Paginate the request history** if requests that bounce many times become realistic.
21. **Consolidate the day-range helper:** the request and audit services each convert dates to UTC day bounds; move it into one shared, tested helper.
22. **Dashboard** (Phase 7) will need counts that respect the same visibility rules as the request list; reuse its query building rather than a second implementation.
23. **URL-persisted filters:** the request list reads `status`, `mine` and `reviewable` from the URL only as starting values; changing a filter does not update the URL.
24. **Recent-activity scaling:** the employee-scoped query filters by creator after scanning the time-ordered index; revisit with measurements.
25. **Automated accessibility and responsive checks:** Playwright with an accessibility scan (for example axe) on the main screens, and screenshots at narrow widths (Phase 9).
26. **Richer page titles:** include the organization or request name in the title.
