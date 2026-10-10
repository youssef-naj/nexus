# ADR-0031: Request assignment and the accessibility pass

- **Status:** Accepted
- **Date:** 2026-10-11

## Context
Reviewers need to say who is expected to handle a submitted request, and the dashboard needs a personal "assigned to you" queue. The interface also needs to work on narrow screens and for keyboard and screen-reader users.

## Decision
- **Advisory assignment.** A reviewer (REQUEST_REVIEW) assigns a SUBMITTED request to an active member who can review (REQUEST_REVIEW) and is not the request's creator, including themselves. Assignment does not restrict who may decide: any reviewer can still approve, reject or request changes, so absences never block a request. Removal is a separate `DELETE ...?version=n`; there is no "PUT null", so a client that omits a field can never unassign by accident.
- **Generic target errors.** An unknown id, a member of another organization, a removed member, a non-reviewer and the creator all return the same 400 for field `membershipId`, so nothing is learned about other members or organizations.
- **Same safeguards as the workflow.** The caller must send the version they saw (409 `STALE_VERSION`); a non-submitted request is 409 `REQUEST_NOT_ASSIGNABLE`; a request the caller cannot see is 404; an employee who created it gets 403. Assigning the person already assigned, or removing when nobody is assigned, changes nothing and writes no history or audit.
- **Locks.** Assigning takes the organization lock in shared mode, re-reads the caller's role, then the request row, in the one fixed order (organization first). Role changes and removals take the exclusive organization lock, so the assignee cannot be removed or demoted halfway through an assignment.
- **Cleanup.** When a member is removed, leaves, or is demoted below a role that can review, their assignments on open requests (submitted or sent back) are cleared in the same transaction with a version bump. Decided requests keep the assignee as a record of who handled them. The cleanup is a consequence of an audited member change and is not a separate history event.
- **History and audit.** Assigning and unassigning are `request_events` rows (action ASSIGN or UNASSIGN, with a target membership under a composite foreign key, and a check that only these two actions have a target), and audit events recording the reference and the assignee's membership id (no names, no emails). History and recent activity read the action as text, so unknown future actions still display.
- **Queues.** `assignedToMe` on the request list returns submitted requests assigned to the caller (reviewers only; any other caller, or a conflicting status, gets an empty page). The dashboard shows the matching count next to "awaiting your review", and the card and the list always agree. A partial index on `(organization_id, assignee_membership_id, status)` serves both.
- **Reviewer picker.** `GET /api/orgs/{orgId}/reviewers` lists active members who can review (capped at 100), requires REQUEST_REVIEW, and marks the caller's own entry so the UI can offer "assign to me".
- **Accessibility and responsive behaviour.** Every route declares a page title; after navigating to another page, focus moves to the main region (not on the first load, and not when only the query string changes); wide tables sit in labelled, keyboard-focusable scroll regions; the header and the organization navigation wrap on narrow screens. These live in a root layout used only by the real router, so component tests are unaffected.

## Alternatives considered
- Exclusive assignment (only the assignee may decide): safer ownership, but blocks work when someone is away, and would need reassignment tooling.
- Clearing assignments on every status change: loses the record of who handled a decided request.
- Recording the cleanup as history events: they would need a system actor; the audited member change already explains them.

## Consequences
- The list of reviewers is capped at 100; very large organizations would need a searchable picker.
- Assigning bumps the request's version, so a reviewer who opened the request before an assignment must reload before deciding.
- Page titles are static per route (they do not include the organization or request name).
- Responsive behaviour and focus handling are verified by component tests and manual checks, not by automated browser tests; Playwright is planned.
