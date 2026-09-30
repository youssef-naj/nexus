# ADR-0010: Workflow state machine and concurrency

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
Requests move through an approval workflow. Two reviewers, or a reviewer and an editor, may act at the same time. Invalid transitions must be impossible.

## Decision
**States:** `DRAFT`, `SUBMITTED`, `CHANGES_REQUESTED`, `APPROVED`, `REJECTED`.

**Allowed transitions**
| From | Action | To | Who |
|---|---|---|---|
| DRAFT | submit | SUBMITTED | creator |
| SUBMITTED | approve | APPROVED | reviewer, not the creator |
| SUBMITTED | reject | REJECTED | reviewer, not the creator |
| SUBMITTED | request changes | CHANGES_REQUESTED | reviewer, not the creator |
| CHANGES_REQUESTED | resubmit | SUBMITTED | creator |

`APPROVED` and `REJECTED` are terminal. Everything else is rejected with 409.

- The transition table lives in the **domain layer** and is unit-tested exhaustively (every state and action pair).
- Transitions run in a single transaction that updates the request and inserts a `request_events` row (actor, action, from, to, optional comment, timestamp).
- Concurrency uses **optimistic locking** (`@Version`) on requests, departments, memberships and organizations. A stale write returns 409 with a problem body.
- Request events are append-only.
- Editing is allowed only in `DRAFT` and `CHANGES_REQUESTED`, and only by the creator (ADR-0007).

## Alternatives considered
- A workflow engine or state-machine library: unnecessary for five states, and hides rules that we want to understand and test.
- Pessimistic locking: reduces conflicts, but adds lock handling and deadlock risk. Optimistic locking with clear 409 responses is enough.

## Consequences
Clients must handle 409 by reloading. Concurrency tests (two simultaneous approvals; edit versus approve) are required.
