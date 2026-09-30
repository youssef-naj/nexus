# ADR-0004: Tenant isolation strategy

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
Organizations share one application. A user of Organization A must never access Organization B's private data. A user may belong to several organizations with a different role in each.

## Decision
Shared PostgreSQL database and shared schema. Isolation is layered:

1. **Organization in the URL path.** Tenant endpoints are `/api/orgs/{orgId}/...`. "Switching organization" means the frontend navigates to another `orgId`. The active organization is **not** stored in the session, and the frontend-supplied `orgId` is never trusted on its own.
2. **A single membership gate.** Every org-scoped request resolves `(authenticated user, orgId)` to an *active membership*. No membership means a 404 (ADR-0005). Tests verify that every org route passes through the gate.
3. **Scoped data access.** Repository and service methods for tenant entities always take `organizationId` (for example `findByIdAndOrganizationId`). Unscoped `findById` on tenant entities is forbidden, enforced by an architecture test.
4. **Database-enforced ownership.** Every tenant table has `organization_id NOT NULL`. `memberships` and `departments` expose `UNIQUE (organization_id, id)`, and referencing tables use **composite foreign keys** such as `(organization_id, department_id)`. A row can therefore not reference another tenant's department or member, even if application code is wrong.
5. **Tenant tables reference memberships, not users.** A creator, assignee or reviewer must be a member of that organization. Membership rows are never deleted, only marked `REVOKED`, so history stays intact.
6. **Negative tests** for cross-tenant read and write attempts on every tenant-owned endpoint.
7. **Later (Phase 8):** evaluate PostgreSQL row-level security as defense in depth, including its connection-pool and transaction-context implications.

## Alternatives considered
- **Schema per tenant / database per tenant:** stronger isolation but heavy migrations and operations for this scope.
- **Active organization stored in the session:** stale-context and confused-deputy risks. Rejected.
- **Hibernate `@TenantId` or filters as the only mechanism:** implicit magic that is easy to bypass with native queries. May be added later as an extra layer, not as the primary defense.

## Consequences
Every query needs the organization ID, which is more explicit code but easier to audit. Composite keys make the schema slightly more verbose and require deliberate JPA mappings.
