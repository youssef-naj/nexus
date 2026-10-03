# Security

Status legend: **[built]** exists and is tested, **[planned]** designed but not implemented yet.
This document describes what the code does today, not what we hope it does. Decisions are recorded in [docs/decisions](decisions/README.md).

## Threat model summary

| Threat | Mitigation | Status |
|---|---|---|
| Cross-tenant data access (IDOR) | Organization in the URL path, membership gate, organization-scoped queries, composite foreign keys, negative tests (ADR-0004) | [planned] Phase 3 |
| Role escalation | Central permission policy, role-assignment rules, last-owner invariant (ADR-0007) | [planned] Phase 3-4 |
| Password guessing and credential stuffing | bcrypt hashing, per-IP and per-account rate limits, generic errors (ADR-0015, ADR-0016) | [built] |
| Account enumeration | Identical responses for registration and login outcomes, equalized timing, silent email limits (ADR-0014, ADR-0015) | [built] |
| Session theft or fixation | HttpOnly SameSite=Lax cookie, session ID rotated at login, server-side revocable sessions (ADR-0003) | [built] |
| CSRF | Cookie-to-header token on every unsafe request, including login and logout | [built] |
| Token theft or replay (email links) | 256-bit random tokens, only the SHA-256 hash stored, single use, expiry, atomic consumption (ADR-0011) | [built] |
| Pre-registration account hijack | Email verification is required before sign-in | [built] |
| Email bombing | Per-IP registration limit and a silent per-address email limit | [built] |
| Secrets in the repository | `.env` ignored, placeholders in `.env.example`, no hard-coded credentials | [built] |
| Vulnerable dependencies | Dependabot, `npm audit` in CI | [built] (Maven scan planned, Phase 8) |
| Audit tampering | Append-only audit log with restricted database privileges (ADR-0012). Audit protection relies on database triggers; a database owner can still remove them. | [built, partial] (triggers; restricted DB role is Phase 9) |
| Platform admin over-reach | Platform endpoints expose metadata only (ADR-0006) | [planned] |

## Authentication [built]

- **Registration** `POST /api/auth/register` always answers `202` with the same body, whether the email is new, already registered, or registered but unverified. The real outcome is delivered by email.
- **Email verification** `POST /api/auth/verify-email`. The link opens a page and the token is only used when the person clicks the button, so mail scanners cannot consume it. The token travels in the request body, not in a URL, which keeps it out of access logs.
- **Login** `POST /api/auth/login` uses Spring Security `formLogin` (form-encoded `email` and `password`).
  - Unknown email, wrong password, over-long password and disabled account return the same `401` body.
  - An unknown email still performs one password hash, so timing does not reveal existence.
  - "Email not verified" (`403`, code `EMAIL_NOT_VERIFIED`) is revealed only after the correct password.
- **Logout** `POST /api/auth/logout` ends the session on the server and expires the cookie.
- **Current user** `GET /api/auth/me` reads the user fresh from the database and ends the session if the account is no longer active.

## Passwords [built]

- Hashed with Spring Security's `DelegatingPasswordEncoder` (bcrypt by default, stored with a `{bcrypt}` prefix so the algorithm can be upgraded without invalidating existing passwords).
- Policy: at least 12 characters, at most 72 **bytes** (bcrypt's input limit; counted in bytes, not characters). No composition rules.
- Never logged: request and command objects redact their `toString()`.
- The session stores no password material (tested).

## Sessions [built]

- Stored in PostgreSQL (Spring Session JDBC); the browser holds only a random `SESSION` identifier.
- Cookie: `HttpOnly`, `SameSite=Lax`. `Secure` must be enabled in deployed configuration (HTTPS), see the limitations below.
- Idle timeout 2 hours. The session's principal name is the **user ID**, so all sessions of one user can be found and revoked.
- Anonymous requests never create sessions (request cache disabled).
- No authentication data is kept in `localStorage` or `sessionStorage` (verified in the browser).

## CSRF [built]

Spring Security's SPA configuration issues an `XSRF-TOKEN` cookie that JavaScript reads and sends back in the `X-XSRF-TOKEN` header on every `POST`, `PUT`, `PATCH` and `DELETE`. The frontend fetches the cookie on demand before its first unsafe request. The cookie is intentionally readable by JavaScript.

## Rate limiting [built]

| Endpoint | Rule |
|---|---|
| Login | 30 attempts per IP per 5 minutes; 5 **failed** attempts per (email, IP) per 15 minutes (a success clears the counter) |
| Registration | 10 per IP per hour |
| Email verification | 30 per IP per 10 minutes |
| Account emails | 3 per address per hour, silently |

Exceeding a limit returns `429` Problem Details with `Retry-After`. A blocked (email, IP) pair stays blocked even with the correct password. Limits are configurable under `nexus.rate-limit.*`.

## Error handling [built]

All errors are Problem Details (RFC 9457, `application/problem+json`), including `401`, `403`, `429` and CSRF failures produced by the security filters. Validation errors add an `errors` map from field name to messages. Unexpected errors return a generic `500` with no stack trace or internal detail.

## Secrets and configuration [built]

- Configuration comes from environment variables; locally they are read from the git-ignored `.env` under the `local` profile.
- `.env.example` contains placeholders only.
- Compose binds PostgreSQL and Mailpit to `127.0.0.1`.

## Authorization [planned]

Arrives with Phases 3 and 4:

- Tenant endpoints live under `/api/orgs/{orgId}/...`. A single membership gate resolves `(user, orgId)` to an active membership. No membership means `404`; a member lacking permission gets `403` (ADR-0005).
- Roles map to permissions in one policy class (ADR-0007). The role-to-permission matrix will be documented here once it exists.
- Every tenant endpoint will have cross-tenant negative tests.

## Known limitations and remaining security work

Honest list, in rough priority order:

1. **Tenant isolation is not built yet.** There are no tenant-owned resources today, so nothing can leak, but the central requirement is still ahead.
2. **`Secure` cookie flag** must be set for the session cookie in every HTTPS deployment (Phase 9 checklist). The CSRF cookie also gets no explicit `SameSite` attribute yet; browsers treat it as `Lax`.
3. **Rate limiter is in memory**: counters are per instance and reset on restart. A shared store is needed to run several instances (ADR-0016).
4. **Session lifetime:** only an idle timeout exists, no absolute lifetime. Disabling an account ends its sessions at the next `/me` call, and the tenant membership gate (Phase 3) will check on every tenant request. There is no "sign out everywhere" feature yet.
5. **No password reset, password change, MFA, breached-password check or CAPTCHA.**
6. **Emails are fire-and-forget:** no retry or outbox. If SMTP fails, the error is logged and the user can register again to get a new link.
7. **Logging policy** (what may appear in logs, including addresses inside third-party error messages) is not formalized yet.
8. **Distributed attacks** from many IPs against one account are not stopped by per-IP rules.
9. **Security headers and CORS** use Spring defaults and have not been reviewed. CORS is unnecessary while the SPA and API share one origin through the proxy.
10. **OpenAPI exposure:** springdoc endpoints are present but reachable only after authentication; their per-environment policy is undecided.
11. **No row-level security** yet; planned as defense in depth (Phase 8).
12. **No Maven vulnerability scan** in CI yet.

Nothing here has had an external security review. This project should not be treated as production-ready.
