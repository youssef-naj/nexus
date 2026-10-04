# ADR-0021: Invitations and the team module

- **Status:** Accepted
- **Date:** 2026-10-04

## Context
Phase 4 adds invitations, member administration and role changes. Putting invitation endpoints in the `membership` module would make it depend on `organization` (for the tenant gate and permissions), while `organization` already depends on `membership`: a cycle.

## Decision
- A new `team` module holds these use-cases and depends on `organization`, `membership`, `identity` and `audit`. Nothing depends on `team`. `membership` keeps the aggregate and its small service API.
- An invitation is pending until accepted, rejected, revoked or expired (computed from `expires_at`). Tokens are 256-bit random values, only their SHA-256 is stored, and they expire after 7 days.
- Accepting requires a signed-in, verified account whose email equals the invited address AND possession of the token. Every other situation (wrong account, expired, replayed, already a member, suspended organization) returns the same generic 400. The token travels in the request body, never the URL.
- Accepting consumes the invitation with a single conditional UPDATE, so replays and races cannot both succeed.
- A revoked member rejoins by reactivating the same membership row, with the invited role.
- Roles can only be granted strictly below the granter's own role, except that an Owner may grant Owner (ADR-0007). The same rule governs revoking or replacing an invitation.
- One pending invitation per (organization, email), enforced by a partial unique index. Inviting again replaces the pending one.
- The invitation email is sent after the transaction commits, on a separate thread, and shares the per-address email budget with account emails. Creating invitations is limited per organization.
- The invited email address is not written to the audit log. Events record the role and the invitation id.
- The database refuses an invitation whose inviter belongs to another organization (composite foreign key).

## Alternatives considered
- Accepting by token alone: a forwarded or leaked link would grant membership to anyone signed in.
- Sending the email from inside the transaction: could announce an invitation that is then rolled back.
- Distinct error messages for each failure: would let someone probe invitations and accounts.

## Consequences
- Invitations to people without an account work (they register and verify first), but the registration page does not yet pre-fill the invited address. The frontend step handles the flow.
- Audit entries cannot name the invitee. Administrators can still see pending invitations in the organization, and the invitation id links the events.
- Email delivery is fire-and-forget; an administrator can re-invite to send a fresh link.