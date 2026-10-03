# ADR-0018: Organizations, memberships and per-request account checks

- **Status:** Accepted
- **Date:** 2026-10-03

## Context
Phase 3 introduces tenants. We need a creation flow, a way to represent a user's role per organization, and a guarantee that disabled accounts lose access immediately.

## Decision
- A user's role lives on a **membership** row (one per user per organization), with status ACTIVE or REVOKED. Memberships are never deleted. The modules (`organization`, `membership`) refer to each other by ID, and organization code reaches memberships only through `MembershipService`.
- Creating an organization is a single transaction: organization row, Owner membership for the creator, and the `ORGANIZATION_CREATED` audit event.
- The **slug** is a readable label derived from the name (accents removed, hyphenated). If taken, 8 random hex characters are appended. Slugs are never used in access decisions; all routes and checks use the UUID. Names are not unique.
- The database guarantees: unique (organization, user), valid roles and statuses, real foreign keys, a slug format check, and `UNIQUE (organization_id, id)` as the target of composite foreign keys.
- `ActiveAccountFilter` re-checks the account on every authenticated request and destroys the session of a disabled account.

## Alternatives considered
- Roles on the user: cannot express different roles in different organizations.
- A global unique organization name: leaks which names exist and blocks legitimate duplicates.
- Checking account status only in `/me` or in the membership gate: leaves other endpoints open to disabled accounts.

## Consequences
- One extra primary-key lookup per authenticated request. A short-lived cache can be added if measurements justify it.
- Any number of organizations per user is allowed for now; a creation limit belongs to the hardening phase.
- Suspended organizations are visible in "my organizations" with their status; blocking their data access comes with the membership gate.