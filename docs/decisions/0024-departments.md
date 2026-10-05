# ADR-0024: Departments

- **Status:** Accepted
- **Date:** 2026-10-05

## Context
Organizations group members into departments. Departments are the first business table owned by a tenant, so they set the pattern every later table follows.

## Decision
- Every row has `organization_id`. Entities are loaded only through organization-scoped queries (`findByIdAndOrganizationId`); an id from another tenant is "not found". The table exposes `UNIQUE (organization_id, id)` as the target of composite foreign keys (department membership and requests, next steps).
- Names are unique per organization **ignoring case**, including deactivated departments, enforced by a unique index on `(organization_id, lower(name))`. The service pre-checks for a friendly 409 `DEPARTMENT_NAME_TAKEN`; the index is the guarantee under races and its violation maps to the same 409. To reuse a name, rename the old department.
- Departments are **deactivated and reactivated, never deleted**. Both operations are idempotent. Deactivated departments stay visible and filterable and keep their history; later steps refuse new assignments and new requests for them.
- Permissions: DEPARTMENT_VIEW for every member, DEPARTMENT_MANAGE for Owners and Admins (ADR-0007). Departments are organizational labels in the MVP and do not restrict who can see requests.
- Updates use `PUT` with the full representation and the version the client saw: a mismatch is 409 `STALE_VERSION`, a true race is 409 from the optimistic lock. An update that changes nothing writes nothing (no version bump, no audit row).
- Listing is paginated (size capped at 100), sorted by a whitelist (`NAME`, `CREATED`), and filterable by active state and by a case-insensitive name search in which `%`, `_` and `\` match literally. Query text is built from fixed fragments; user values are bound parameters.
- Audit events: created (name), updated (from and to name when the name changed), deactivated, reactivated. Descriptions are never logged.

## Alternatives considered
- Hard deletion: breaks history once requests reference departments.
- Unique names only among active departments: allows ambiguous history and surprising reactivation conflicts.
- Spring Data `Specification` or free-form sort strings: more flexible, but easier to misuse than a whitelist.

## Consequences
- A department's name can only be reused after renaming the old one.
- Descriptions are limited to 500 characters of plain text (newlines allowed, other control characters rejected).
