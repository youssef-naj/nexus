# ADR-0020: Organization in the frontend route; permissions are hints

- **Status:** Accepted
- **Date:** 2026-10-03

## Context
The frontend needs to know which organization the user is working in, and must not turn client-side checks into a security boundary.

## Decision
- The organization is part of the route: `/orgs/:orgId/...`. No "active organization" is stored in the session, `localStorage` or any global store. Switching means navigating.
- The tenant layout loads `GET /api/orgs/{id}`, which passes through the server-side gate, and provides the member's role and permissions to its children. `<Can permission>` hides controls the role doesn't allow.
- Permissions are UI hints only. Every action is authorized again on the server; hiding a button is never the protection.
- A 404 from the organization route renders one neutral screen for "does not exist" and "not a member" (ADR-0005). A 403 `ORGANIZATION_SUSPENDED` renders a dedicated message.
- All responses are validated with Zod. Permission names are kept as strings so a newer server does not break an older client.

## Alternatives considered
- Storing the active organization in browser storage or the session: stale state across tabs and users, and a confused-deputy risk.
- Deriving permissions on the client from the role alone: duplicates the server's matrix and drifts from it.

## Consequences
- One request when entering an organization, cached by TanStack Query and cleared on logout.
- The `PERMISSIONS` list in the frontend mirrors the backend enum; a mismatch only affects which buttons appear, never access.