# Security

Status legend: **[built]** exists and is tested, **[planned]** designed but not implemented yet.
This document describes what the code does today, not what we hope it does. Decisions are recorded in [docs/decisions](decisions/README.md).

## Threat model summary

| Threat | Mitigation | Status |
|---|---|---|
| Cross-tenant data access (IDOR) | Organization in the URL path, a single membership gate, organization-scoped queries, composite foreign keys, cross-tenant negative tests (ADR-0004, ADR-0019) | [built] (row-level security still planned) |
| Role escalation | Central permission policy and role-assignment rules on invitation create, revoke and supersede (ADR-0007) | [built, partial] — acceptance does not re-check the inviter, and there is no last-owner invariant |
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
- Idle timeout 2 hours, with no absolute lifetime. The session's principal name is the **user ID**, so every session of one user can be found — but no "sign out everywhere" feature uses that yet.
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

## Authorization [built]

- Tenant endpoints live under `/api/orgs/{orgId}/...`. `OrgScopeFilter` resolves `(user, orgId)` to an **active** membership before any controller runs. The organization id is taken from the URL path only — never from a header, a request body or the session. No membership means `404`; a member lacking permission gets `403` (ADR-0005).
- Roles map to permissions in one policy class (ADR-0007, ADR-0019). Call sites check **permissions**, never role names; role names appear only in the role-hierarchy policy that decides who may grant what.
- `TenantRoutesInventoryIntegrationTest` enumerates every registered tenant route and asserts that a non-member gets `404` on each, so a new route cannot be added without passing the gate.

| Permission | Owner | Admin | Manager | Employee |
|---|---|---|---|---|
| ORGANIZATION_VIEW, MEMBER_VIEW, DEPARTMENT_VIEW, REQUEST_CREATE | yes | yes | yes | yes |
| REQUEST_VIEW_ALL, REQUEST_REVIEW | yes | yes | yes | no |
| ORGANIZATION_UPDATE, MEMBER_INVITE, MEMBER_REVOKE, ROLE_ASSIGN, DEPARTMENT_MANAGE, AUDIT_VIEW | yes | yes | no | no |


## Invitations [built]

- `POST /api/orgs/{orgId}/invitations` creates an invitation for an email address at a role the inviter is allowed to grant (strictly below their own, except that an Owner may grant Owner).
- The token is 256 random bits; only its SHA-256 hash is stored and it is never returned in a response. It expires after 7 days, and expiry is enforced **inside** the consuming `UPDATE`, not only in Java.
- Acceptance requires a signed-in, email-verified account whose address matches the invitation, and an invitation that is still `PENDING`. Replay is impossible: a single conditional update decides the winner.
- A wrong account, an expired token, an already-decided invitation, an existing membership and a suspended organization all return the **same** generic `400` (ADR-0005).
- Rejection is available to the invitee and revocation to the inviter. A previously revoked member who is invited again is reactivated rather than duplicated.

## Gate [built]

One filter protects the whole tenant surface, so authorization cannot be forgotten per route:

- `OrgScopeFilter` matches `/api/orgs/{orgId}/**`, resolves the active membership, and passes the result as a **request attribute**. There is no `ThreadLocal` anywhere in the application, so a scope cannot leak across threads or into `@Async` work.
- A controller that asks for `@CurrentOrg` on an ungated route fails closed instead of running with no organization.
- `ActiveAccountFilter` re-reads the account on **every** request and requires `ACTIVE` plus a verified email, destroying the session and returning `401` otherwise (ADR-0018). Disabling an account therefore takes effect on the next request rather than at the next login.
- Tenant ownership is also enforced by the database: `memberships` exposes `UNIQUE (organization_id, id)` and invitations reference `(organization_id, invited_by_membership_id)`, so a row cannot point at another organization's member even if application code is wrong.


## Known limitations and remaining security work

Honest list, in rough priority order:

1. **Tenant isolation is in place but only partially tested.** The gate, the organization-scoped queries and the composite foreign keys are built, and the cross-tenant harness proves reads are rejected in both directions. It does **not** yet drive a *write* endpoint through the same assertions, and the route-inventory test only inspects paths under `/api/orgs/{`, so a tenant route added under another prefix would not be checked.
2. **`Secure` cookie flag** must be set for the session cookie in every HTTPS deployment (Phase 9 checklist). The CSRF cookie also gets no explicit `SameSite` attribute yet; browsers treat it as `Lax`.
3. **Rate limiter is in memory**: counters are per instance and reset on restart. A shared store is needed to run several instances (ADR-0016).
4. **Session lifetime:** only an idle timeout exists, no absolute lifetime. Disabled accounts lose access on their next authenticated request (ADR-0018).
5. **No password reset, password change, MFA, breached-password check or CAPTCHA.**
6. **Emails are fire-and-forget:** no retry or outbox. If SMTP fails, the error is logged and the user can register again to get a new link.
7. **Logging policy** (what may appear in logs, including addresses inside third-party error messages) is not formalized yet.
8. **Distributed attacks** from many IPs against one account are not stopped by per-IP rules.
9. **Security headers and CORS** use Spring defaults and have not been reviewed. CORS is unnecessary while the SPA and API share one origin through the proxy.
10. **OpenAPI exposure:** springdoc endpoints are present but reachable only after authentication; their per-environment policy is undecided.
11. **No row-level security** yet; planned as defense in depth (Phase 8).
12. **No Maven vulnerability scan** in CI yet.
13. **Invitation acceptance does not re-check the inviter's authority**, and pending invitations are not revoked when a member is demoted or removed: an invitation created by an Owner who later loses that role still grants the role it was created with. Latent today, because no role-change or member-removal endpoint exists yet; it must be fixed when member management lands.
14. **No last-owner invariant.** ADR-0007 requires an organization to keep at least one active Owner; nothing enforces it, so member removal could leave a tenant administratively dead.
15. **Invitation emails are fire-and-forget**, invitees without an account must register first, and the per-address email budget is applied after commit, so an invitation can be created while its email is silently suppressed for up to an hour.

Nothing here has had an external security review. This project should not be treated as production-ready.
