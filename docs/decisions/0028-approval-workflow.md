# ADR-0028: Approval workflow

- **Status:** Accepted
- **Date:** 2026-10-08

## Context
Requests must move through a reviewed lifecycle with a trustworthy history, without a configurable workflow engine (ADR-0010).

## Decision
- **Actions and transitions** live in one domain enum: SUBMIT (from DRAFT or CHANGES_REQUESTED to SUBMITTED), APPROVE (to APPROVED), REJECT (to REJECTED) and REQUEST_CHANGES (to CHANGES_REQUESTED), the last three only from SUBMITTED. APPROVED and REJECTED are terminal. A unit test walks every action and status.
- **One endpoint:** `POST /requests/{id}/transitions` with the action, the version the user saw and an optional comment. Anything else is refused: an impossible transition is 409 `INVALID_TRANSITION`, a stale version is 409 `STALE_VERSION`. A reviewer therefore can only decide on what they actually saw, even if the creator edited and resubmitted in between.
- **Who:** only the creator submits (403 `NOT_REQUEST_OWNER`). Review decisions require REQUEST_REVIEW (Owner, Admin, Manager), and nobody may review a request they created, whatever their role (403 `SELF_REVIEW_NOT_ALLOWED`). This is the MVP rule; an organization setting to allow it is deferred. A request the caller may not see is 404.
- **Comments:** REJECT and REQUEST_CHANGES require one; SUBMIT and APPROVE accept one. Plain text, at most 1000 characters, no control characters except line breaks and tabs. Comments appear in the request history only, never in the audit log.
- **Concurrency and lock order:** every decision takes a shared lock on the organization row, re-reads the caller's membership and role under it, then takes an exclusive lock on the request row. Role changes and removals take the exclusive organization lock (ADR-0022), so a demoted or removed reviewer cannot slip a decision through, while many reviewers can work in parallel. The request lock makes simultaneous decisions on one request queue up, so exactly one wins. The order is always organization first, then the request or department.
- **History:** a `request_events` row for every transition: actor, action, from and to status, comment, time. The table is append-only through database triggers and has composite foreign keys to the request and the actor, so history can neither be rewritten nor point across tenants. Creating a draft is not a business event; the history starts at submission. Anyone who can see a request can read its history.
- **Audit:** submission and each decision write an audit event in the same transaction, with the request's reference number as the only metadata.
- **UI hints:** the request detail includes the actions available to the viewer; the server checks them again on use.

## Alternatives considered
- A separate endpoint per action: more code and more routes to isolate-test, with no benefit.
- Optimistic locking alone for decisions: two reviewers racing would both read SUBMITTED and one would fail with an obscure conflict; the lock gives a clean 409 and a single recorded decision.
- Letting owners approve their own requests: simpler for small teams, but contradicts the separation of duties the feature exists for.

## Consequences
- Decisions in one organization briefly share a lock with each other and block role changes; fine for the expected volume.
- A request cannot be withdrawn or cancelled by its creator after submission, and there is no reassignment; both are deferred.
- Reviewers see requests only as listed; there is no "assigned to me" queue until assignment exists.
