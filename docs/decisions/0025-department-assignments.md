# ADR-0025: Department assignments

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
Members belong to departments (many-to-many). The assignment must never cross an organization boundary and must stay consistent when members are removed or departments deactivated.

## Decision
- The `department_memberships` table stores `organization_id` beside the department and membership and has two **composite foreign keys**: `(organization_id, department_id)` to departments and `(organization_id, membership_id)` to memberships. The database therefore refuses a cross-organization assignment even from raw SQL. Tests exercise this directly.
- Assignments are plain SQL rows with no JPA entity (composite key, no behaviour). Assign is `INSERT ... ON CONFLICT DO NOTHING` and unassign is a scoped `DELETE`; both are idempotent and answer 204, and only real changes are audited (`DEPARTMENT_MEMBER_ADDED`, `DEPARTMENT_MEMBER_REMOVED` with the membership id).
- Assigning takes the **organization lock** shared with member administration (ADR-0022) and a **row lock on the department**. Assigning cannot race with removing the member (no assignment to a revoked member survives) or with deactivating the department (one wins; the loser gets `409 DEPARTMENT_INACTIVE`).
- Removing or leaving an organization clears the member's assignments in the same transaction. Rejoining later starts with no departments.
- Only active members can be assigned or listed. Inactive departments refuse new members but can be emptied.
- Permissions: viewing uses DEPARTMENT_VIEW; assigning and unassigning use DEPARTMENT_MANAGE. Email addresses in the department member list are shown only to roles that may invite members.
- The member listing joins memberships and users read-only, the same documented exception as the organization member list.

## Alternatives considered
- Keeping assignments after revocation and filtering by status: silently restores old assignments when a member is invited back.
- A JPA many-to-many mapping: hides the composite keys and the idempotent insert behind framework behaviour.
- Rejecting a repeated assignment with 409: forces clients to read before writing, and races anyway.

## Consequences
- `team` now depends on `department` (member removal clears assignments); nothing depends on `team`, so there is no cycle.
- All membership-related changes in one organization are serialized by one lock. Fine for administrative actions; revisit if measured contention appears.
- No assignment history is kept beyond the audit log.
