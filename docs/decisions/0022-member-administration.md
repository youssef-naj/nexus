# ADR-0022: Member administration

- **Status:** Accepted
- **Date:** 2026-10-04

## Context
Administrators need to list members, change roles, remove members, and members need to leave, without breaking the rules from ADR-0007 (no escalation, no self-management, always an owner) even under concurrency.

## Decision
- **Rules.** A role change or removal requires ROLE_ASSIGN / MEMBER_REVOKE. The actor must be able to grant both the target's current role and the new role, so an Admin cannot touch Admins or Owners and cannot promote above Manager. Nobody changes or removes themselves; leaving is a separate action. An organization always keeps at least one active Owner, enforced in one method used by role changes, removals and leaving.
- **Concurrency.** Every operation takes a pessimistic row lock on the organization (`SELECT ... FOR UPDATE`) and then re-reads the caller's own membership, because the gate's snapshot may be stale. This serializes membership changes per organization, so two owners demoting each other at once cannot leave zero owners. Separately, each member carries a version: a change request includes the version the client saw, and a mismatch returns 409 `STALE_VERSION` (lost-update protection across user think time). Hibernate's optimistic lock is the backstop and maps to 409 `CONCURRENT_MODIFICATION`.
- **Revocation** marks the membership REVOKED (never deletes). The gate denies access on the member's next request.
- **List.** A paginated, name-sorted, role-filterable read-only join over memberships and users. This is the one exception to "modules do not read each other's tables": it only reads, and all writes go through each module's service.
- **Privacy.** Everyone sees names and roles; email addresses are returned only to roles that can invite members.
- **Audit.** Role changes (from and to role), removals and departures record role names only, never emails.
- **Errors.** 403 with a code for rule violations (CANNOT_CHANGE_OWN_ROLE, CANNOT_REMOVE_SELF), 409 for conflicts (STALE_VERSION, LAST_OWNER), 404 for ids outside the organization.

## Alternatives considered
- Optimistic locking alone: cannot protect a rule that spans several rows (the owner count).
- Trusting the gate's role snapshot: a caller demoted a moment ago could still act.
- A cross-module repository for the list: leaks entities across modules; a read model keeps writes encapsulated.

## Consequences
- Membership changes within one organization are serialized, which is fine for administrative actions and keeps the invariants simple.
- Ownership transfer is "promote another member to Owner, then step down"; there is no single-step transfer.
- The member list does not include revoked members. A history view could be added later.
