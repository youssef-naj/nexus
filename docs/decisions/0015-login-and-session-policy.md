# ADR-0015: Login errors and session principal

- **Status:** Accepted
- **Date:** 2026-10-01

## Context
Login responses can reveal which accounts exist, and sessions can leak secrets or outlive account changes.

## Decision
- Login uses Spring Security `formLogin` (form-encoded `email` and `password`) with CSRF protection.
- Unknown email, wrong password, over-long password and disabled account all return the same 401. An unknown email still costs one password hash, to equalize timing.
- "Email not verified" (403, code EMAIL_NOT_VERIFIED) is returned only after the correct password.
- The session principal contains id, email and display name only. Its name is the user ID, so sessions are indexed by user and can be revoked per user.
- Sessions live in PostgreSQL (Spring Session JDBC, Flyway-managed tables), with a 2-hour idle timeout. The cookie is HttpOnly and SameSite=Lax.
- The request cache is disabled so anonymous requests never create sessions.
- `/api/auth/me` reads the user fresh from the database and ends the session if the account is no longer active.

## Consequences
No absolute session lifetime yet. Existing sessions of a disabled user keep working until a check runs: `/me` checks now, and the tenant membership gate (Phase 3) will check on every tenant request. The `Secure` cookie flag must be enabled in deployed configuration. Login is not rate-limited yet (next step).