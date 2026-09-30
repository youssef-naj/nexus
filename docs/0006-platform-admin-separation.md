# ADR-0006: Platform administration separated from tenant access

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
Platform operators must manage the service without gaining automatic access to customer data.

## Decision
- The platform role is a property of the **user** (`users.platform_role`), separate from organization memberships.
- Platform endpoints live under `/api/platform/**` and expose **organization metadata only** (name, status, member count): list, suspend, reactivate.
- A platform administrator has **no implicit tenant permissions**. Being a platform admin does not pass the membership gate of ADR-0004.
- Every platform action is written to the audit log (ADR-0012) with a null organization scope.
- Any future support access to tenant data must be an explicit, time-boxed, audited "break-glass" feature. It is out of scope for the MVP.

## Alternatives considered
A superuser that bypasses tenant checks: convenient, but one bug or stolen account exposes every tenant. Rejected.

## Consequences
Platform staff cannot debug tenant data directly. Support workflows must rely on metadata, logs and, later, a controlled break-glass process.
