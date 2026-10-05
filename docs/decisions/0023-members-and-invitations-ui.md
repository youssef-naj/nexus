# ADR-0023: Members and invitations UI

- **Status:** Accepted
- **Date:** 2026-10-04

## Context
The frontend must expose member administration and invitations without becoming a security boundary, and must handle concurrent edits and unusable invitation links gracefully.

## Decision
- Controls are shown from the member's role and permissions (hints). `grantableRoles` and `canManageMember` mirror the server's `RoleAssignmentPolicy`; the server still enforces everything, and its error codes are translated into sentences.
- A role change sends the version the page last saw. A 409 (`STALE_VERSION` or `CONCURRENT_MODIFICATION`) reloads the list and tells the user why.
- Destructive actions (remove, leave) use an inline "Confirm" step instead of modal dialogs; role pickers are native `<select>` elements. Both are accessible by default and independent of the component library; modals can be reconsidered in the polish phase.
- The accept page requires a signed-in session. It previews the invitation (spending nothing), then accepts or declines on an explicit click. Every failure to preview shows one generic message that suggests checking which address you are signed in with. The login page shows a reminder when it was reached from an invitation link.
- All organization data is cached under `["organizations", orgId, ...]`, so logout clears it and leaving invalidates it. No token or invitation data is written to browser storage.

## Alternatives considered
- Modal confirmation dialogs: richer, but more library surface to test and maintain at this stage.
- Revealing why an invitation failed: would help probing and contradicts ADR-0021.

## Consequences
- An invitee without an account must register, verify, and then open the link again; there is no automatic carry-over through registration.
- The invitation token remains in the URL from the email until the page is left. Adding a `Referrer-Policy` response header is on the hardening list (Phase 8).
