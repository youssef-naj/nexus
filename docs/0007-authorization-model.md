# ADR-0007: Authorization model

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
Users hold different roles in different organizations. Rules must be centralized, testable and extensible, and the server must enforce them on every operation.

## Decision
- Each membership carries **one role**: `OWNER`, `ADMIN`, `MANAGER` or `EMPLOYEE`.
- Roles map to **permissions** (an enum, for example `REQUEST_CREATE`, `REQUEST_REVIEW`, `MEMBER_INVITE`, `ROLE_ASSIGN`, `AUDIT_VIEW`) in one central policy class. Code checks permissions, not role names.
- Resource-level rules live in the same policy layer: an Employee sees only their own requests, only the creator edits a draft, and nobody reviews their own request.
- **Reviewer scope (MVP):** Managers, Admins and Owners may review any request in their organization. Department-scoped review is a later enhancement.
- **Self-approval is always forbidden** in the MVP. A configurable organization policy may be added later and documented.
- **Role escalation rules:** a user may assign only roles strictly below their own, except that an Owner may assign Owner. Users cannot change their own role. An organization must always keep at least one active Owner.
- Authorization is enforced in application services and the membership gate. Frontend guards are only usability measures.

## Alternatives considered
- Method-level annotations only (`@PreAuthorize("hasRole(...)")`): cannot express organization-specific and resource-specific rules cleanly on their own. They may complement the policy class.
- A full policy engine or ABAC library: unnecessary for four roles.

## Consequences
One place to read and test all rules. The role-to-permission matrix must be documented in `docs/security.md` and covered by parameterized tests.
