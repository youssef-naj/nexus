# ADR-0026: Service requests

- **Status:** Accepted
- **Date:** 2026-10-06

## Context
Service requests are the core business object. They must be private to their creator unless a role grants wider visibility, carry readable per-organization reference numbers, and stay consistent under concurrency, without yet implementing the approval workflow.

## Decision
- **Fields:** organization, reference, title (150), description (5000, optional), category, status, creator, optional assignee, optional department, optional due date, timestamps and a version. Categories are a fixed list (IT support, Facilities, HR, Finance, Other) enforced by a CHECK constraint. Statuses are DRAFT, SUBMITTED, CHANGES_REQUESTED, APPROVED, REJECTED; only drafts exist until the workflow phase.
- **Tenant integrity:** the creator, assignee and department are referenced through composite foreign keys `(organization_id, ...)`, so the database refuses cross-organization references. The creator is a membership, not a user, so history survives removal of the member.
- **Reference numbers** ("REQ-000042") come from the organization's counter: one `UPDATE ... RETURNING` inside the creation transaction. The row lock serializes creators of one organization, a rolled-back creation rolls the number back (no gaps), and `UNIQUE (organization_id, reference)` is the safety net.
- **Creation order of locks:** the organization row (through the counter) first, then the creator's membership is re-read, then the department is locked in share mode. Removing a member takes the same organization lock, so a just-removed member cannot create a request, and a department cannot be deactivated between the check and the insert.
- **Visibility:** viewing your own requests needs only membership. REQUEST_VIEW_ALL (Owner, Admin, Manager) shows every request of the organization. Visibility is part of the SQL: an employee's query is always restricted to their own membership, and filters can narrow but never widen it. A request the caller may not see is `404`, indistinguishable from a missing one.
- **Editing:** only the creator, and only while the status is DRAFT or CHANGES_REQUESTED. Answers: not visible 404, visible but not the creator 403 `NOT_REQUEST_OWNER`, wrong status 409 `REQUEST_NOT_EDITABLE`, stale version 409 `STALE_VERSION`, race 409 from the optimistic lock. An edit that changes nothing writes nothing. Rules apply only to what changes: an unchanged past due date or an unchanged, since-deactivated department may stay.
- **Due dates** must be today or later and within ten years, on creation and when changed.
- **Listing:** paginated (size capped at 100), sorted by a whitelist (created, updated, due date with empty dates last), filterable by status, category, department, creator (or `mine`), creation date range and a case-insensitive search of title and reference in which `%`, `_` and `\` match literally. The list returns summaries without descriptions.
- **Read model:** the list and the detail are read-only joins over requests, memberships, users and departments (same documented exception as ADR-0022).
- **Abuse:** creating requests is limited per member (100 per hour by default).
- **Audit:** drafts are private working state and are not audited. Significant business events (submission, review decisions) are recorded as request events and audit events from the workflow phase.
- **Not in this step:** submitting, reviewing, the assignee API, deleting or cancelling drafts.

## Alternatives considered
- Per-department visibility: more rules and tests for little MVP value; departments stay organizational labels.
- A database sequence per organization: needs DDL at organization creation and allows gaps; the counter row is simpler and gap-free.
- Hiding invisible requests with 403: leaks which request ids exist.

## Consequences
- Requests created in one organization serialize briefly on its counter row; acceptable for the expected volume.
- A reference above 999999 grows to seven digits instead of failing.
- Users can end up with drafts they cannot remove until a cancel or delete action exists.
