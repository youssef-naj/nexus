# MVP status

Checked against the original acceptance criteria. Updated at the end of each phase. Last update: Phase 5, step 3 (service requests, API and tests).

## Acceptance criteria

| Criterion | Status | Evidence |
|---|---|---|
| A developer can start the application from a clean checkout using documented instructions | Done | README quick start, tested from a fresh clone |
| The database can be created and migrated reproducibly | Done | Flyway V1 to V6, Testcontainers runs every migration on a fresh PostgreSQL |
| Users can create, submit, review, approve, reject and track internal service requests | Partly | Create, view, edit, filter and paginate are built (API and tests); submit and review come in the approval phase; screens come next || Organization membership and role checks are enforced on the server | Done | Tenant gate, permission policy, role-grant rules |
| Cross-tenant read and write attempts are tested and rejected | Done for everything built so far | Isolation battery on organizations, members, invitations, departments, assignments and requests; route-inventory test; foreign-id tests; raw-SQL tests of every composite foreign key || Users can create, submit, review, approve, reject and track internal service requests | Not yet | Phases 5 and 6 |
| Important actions are auditable | Partly | Eight event families recorded; request and approval events and the audit viewer come in Phase 6 |
| The React frontend handles validation, loading and errors | Done for existing screens | Component tests for each state |
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
| C. Departments | API complete (create, list, update, deactivate, assign members); screens next |
| D. Internal service requests | Create, view, edit drafts, filter, paginate (API); submit comes with the approval phase; screens next |
| E. Approval workflow | Phase 6 |
| F. Audit history | Recording built; viewing in Phase 6 |
| G. Dashboard | Phase 7 |

## Technical debt and open items

Scheduled so nothing is forgotten:

1. **Demo data and demo accounts:** a repeatable seed for local use (Phase 9, possibly earlier).
2. **OpenAPI:** decide per-environment exposure, document error codes, publish the contract (Phase 8 or 9).
3. **Platform administration:** metadata-only endpoints, suspend and reactivate organizations, audited (Phase 8).
4. **Organization settings:** rename and the self-approval policy setting, with audit events.
5. **Audit viewer:** read endpoint and screen for Owners and Admins (Phase 6).
6. **Security headers:** `Referrer-Policy` (tokens appear in page URLs), Content-Security-Policy, explicit `SameSite` for the CSRF cookie (Phase 8).
7. **Architecture tests** (ArchUnit) enforcing the module rules and the "tenant repositories are always scoped" rule (Phase 8).
8. **Playwright** end-to-end tests for the main workflows (Phase 9).
9. **Maven vulnerability scan** in CI (Phase 8).
10. **Deployment:** HTTPS, `Secure` cookies, restricted database runtime role, backups, logs and health checks (Phase 9).
11. **Frontend for departments and requests** (Phase 5, step 4).
12. **Assignee API and the "assigned to me" index** (approval phase).
13. **Cancel or delete drafts** (decide with the workflow).
14. **LIKE escaping is duplicated** in the department and request search classes; consolidate into one tested helper.
