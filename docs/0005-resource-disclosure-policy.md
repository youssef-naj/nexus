# ADR-0005: Resource disclosure policy (401 / 403 / 404)

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
Returning 403 for resources in another tenant confirms that they exist. That leaks information and helps enumeration.

## Decision
| Situation | Response |
|---|---|
| Not authenticated, or session expired | **401** |
| Authenticated, but no active membership in the organization | **404** (indistinguishable from a non-existent organization) |
| Active member, resource does not exist in **that** organization | **404** |
| Active member, resource visible to them, but the role lacks permission for the action | **403** |
| Validation failure | **400** (field errors in the problem body) |
| Optimistic-lock or invalid state-transition conflict | **409** |

Problem-detail bodies for 404 responses are identical whether the resource is missing or belongs to another tenant.

## Alternatives considered
Always returning 403 for anything not allowed: simpler, but discloses existence across tenants.

## Consequences
Slightly harder debugging for legitimate users, mitigated by server-side logs that record the real reason (not exposed to the client). Tests assert both the status code and body shape.
