# ADR-0019: Tenant gate and permission policy

- **Status:** Accepted
- **Date:** 2026-10-03

## Context
Every tenant request must be tied to an active membership of the organization in its URL (ADR-0004), with a non-disclosing response for outsiders (ADR-0005). The check must be impossible to forget.

## Decision
- A servlet filter (`OrgScopeFilter`), registered after Spring Security's chain, inspects every path matching `/api/orgs/{id}/...` before any controller runs. It resolves (authenticated user, organization id) to an ACTIVE membership. Not a member, unknown organization, revoked member and a malformed id all produce the same constant 404 Problem body.
- A member of a SUSPENDED organization receives 403 `ORGANIZATION_SUSPENDED`. The suspension check happens after membership is proven, so outsiders learn nothing.
- The membership is looked up before the organization, so existing and missing organizations cost the same.
- The result is placed in the request as `OrgContext`; controllers receive it through `@CurrentOrg`. If it is missing, the request fails with an error (fail closed).
- Permissions are an enum that lists, for each permission, the roles holding it (ADR-0007). `AccessPolicy.require` throws `AccessDeniedException`, rendered as a 403 Problem. Resource-level rules live in the services that own the resources.
- Tests: a reusable `TenantWorld` fixture and `CrossTenantAssertions.assertIsolated` battery for every tenant endpoint, plus a route-inventory test that fails if any `/api/orgs/{id}/...` route is reachable by a non-member.

## Alternatives considered
- A Spring Security `AuthorizationManager`: denial becomes a 403, which cannot express the required 404 without extra machinery.
- A check inside each controller or service: relies on every author remembering it.
- Hibernate filters or row-level security as the primary mechanism: kept as later defense in depth (Phase 8).

## Consequences
- Two small lookups per tenant request (membership, organization). Caching can be added if measurements justify it.
- Authorization of the action itself (permissions, resource rules) still happens in services; the gate only establishes membership and role.
- Every new tenant endpoint must add its `assertIsolated` call; the inventory test enforces gating but not the other assertions.