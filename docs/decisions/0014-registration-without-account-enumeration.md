# ADR-0014: Registration without account enumeration

- **Status:** Accepted
- **Date:** 2026-09-30

## Context
A registration endpoint that answers "email already in use" lets anyone test which addresses have accounts.

## Decision
The registration use case returns an outcome (`CREATED` or `ALREADY_REGISTERED`). The HTTP layer answers both with the same response (202 Accepted and a generic message). Users learn the real outcome by email: a verification link for a new account, or a notice that an account already exists. The password is hashed before the duplicate check so both paths take similar time. Passwords must be at least 12 characters and at most 72 bytes (bcrypt's input limit).

## Alternatives considered
- Returning 409 for duplicates: better immediate feedback, but leaks account existence.
- Composition rules (digits, symbols): more friction, little security gain.

## Consequences
Users cannot see "email taken" in the form, so the email step must work well (Mailpit locally). Rate limiting on registration remains necessary because hashing is deliberately expensive.