# ADR-0011: Secret tokens and email verification

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
Invitations, email verification and password reset all rely on single-use secrets sent by email. Leaking or reusing them would allow account or membership takeover. An attacker could also pre-register a victim's email address.

## Decision
- Tokens are **256-bit random values** from `SecureRandom`, encoded as base64url.
- Only a **SHA-256 hash** of the token is stored. The raw token exists only in the email link.
- Tokens are **single-use** (`used_at`) and **expire**. Proposed lifetimes: invitations 7 days, email verification 24 hours, password reset 1 hour.
- **Email verification is mandatory** before an account may accept an invitation or create an organization.
- Accepting an invitation requires an authenticated, verified user whose email equals the invited email, plus possession of the token. Any other account gets a generic failure.
- Re-issuing a token invalidates earlier unused tokens of the same type for that target.
- Raw tokens, and full links containing them, are never logged or written to the audit log.
- Locally, emails go to Mailpit, not to real recipients.

## Alternatives considered
- Storing raw tokens: a database leak would expose usable secrets. Rejected.
- Signed stateless tokens (JWT): harder to make single-use and revocable.

## Consequences
Extra registration step, in exchange for closing the pre-hijacking gap. A small scheduled cleanup removes expired tokens.
