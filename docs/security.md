# Security

Status legend: **[built]** exists and is tested, **[planned]** designed but not implemented yet.
This document describes what the code does today, not what we hope it does. Decisions are recorded in [docs/decisions](decisions/README.md).

## Threat model summary

| Threat | Mitigation | Status |
|---|---|---|
| Cross-tenant data access (IDOR) | Organization in the URL path, membership gate before any controller, organization-scoped queries, composite foreign keys, a shared isolation test battery and a route-inventory test (ADR-0004, ADR-0019) | [built] for organizations, members and invitations; every new endpoint must reuse the harness |
| Role escalation | One role-grant policy used by invitations and member changes; nobody changes their own role; an organization always keeps an Owner (ADR-0007, ADR-0022) | [built] |
| Lost updates and races between administrators | Per-organization row lock, caller re-check under the lock, per-member version (409), optimistic locking backstop (ADR-0022) | [built] |
| Password guessing and credential stuffing | bcrypt, per-IP and per-account rate limits, generic errors (ADR-0015, ADR-0016) | [built] |
| Account and organization enumeration | Identical responses for registration, login, invitation and organization lookups; equalized timing (ADR-0005, ADR-0014, ADR-0015) | [built] |
| Session theft or fixation | HttpOnly SameSite=Lax cookie, session ID rotated at login, server-side revocable sessions (ADR-0003) | [built] |
| Disabled or removed users keeping access | Account re-checked on every authenticated request; membership re-checked on every tenant request (ADR-0018, ADR-0019) | [built] |
| CSRF | Cookie-to-header token on every unsafe request, including login and logout | [built] |
| Token theft or replay (email links) | 256-bit random tokens, only the SHA-256 hash stored, single use, expiry, atomic consumption, bound to the invited verified email (ADR-0011, ADR-0021) | [built] |
| Pre-registration account hijack | Email verification before sign-in or accepting an invitation | [built] |
| Email bombing | Per-IP registration limit, per-organization invitation limit, silent per-address email limit | [built] |
| Secrets in the repository | `.env` ignored, placeholders in `.env.example`, no hard-coded credentials | [built] |
| Vulnerable dependencies | Dependabot, `npm audit` in CI | [built] (Maven scan planned, Phase 8) |
| Audit tampering | Append-only audit log: database triggers reject UPDATE, DELETE and TRUNCATE (ADR-0017) | [built, partial]: a database owner can still remove the triggers |
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
- **Per-request account check:** a filter re-checks the account on every authenticated request. A disabled account has its session destroyed and receives `401` immediately.

## Passwords [built]

- Hashed with Spring Security's `DelegatingPasswordEncoder` (bcrypt by default, stored with a `{bcrypt}` prefix so the algorithm can be upgraded without invalidating existing passwords).
- Policy: at least 12 characters, at most 72 **bytes** (bcrypt's input limit; counted in bytes, not characters). No composition rules.
- Never logged: request and command objects redact their `toString()`. The session stores no password material (tested).

## Sessions and CSRF [built]

- Sessions live in PostgreSQL (Spring Session JDBC); the browser holds only a random `SESSION` identifier. Cookie: `HttpOnly`, `SameSite=Lax`. Idle timeout 2 hours. The session's principal name is the **user ID**, so all sessions of one user can be found and revoked.
- Anonymous requests never create sessions. No authentication data is kept in `localStorage` or `sessionStorage` (verified in the browser).
- Spring Security's SPA configuration issues an `XSRF-TOKEN` cookie that JavaScript reads and sends in the `X-XSRF-TOKEN` header on every unsafe request. The cookie is intentionally readable by JavaScript.

## Rate limiting [built]

| Endpoint | Rule |
|---|---|
| Login | 30 attempts per IP per 5 minutes; 5 **failed** attempts per (email, IP) per 15 minutes (a success clears the counter) |
| Registration | 10 per IP per hour |
| Email verification and invitation preview, accept, reject | 30 per IP per 10 minutes |
| Creating invitations | 20 per organization per hour |
| Account and invitation emails | 3 per address per hour, silently (nothing changes in the response) |

Exceeding a limit returns `429` Problem Details with `Retry-After`. A blocked (email, IP) pair stays blocked even with the correct password. Limits are configurable under `nexus.rate-limit.*`.

## Tenant isolation [built]

Every request to `/api/orgs/{orgId}/...` passes a servlet filter (the gate) before any controller runs. It resolves the signed-in user and the organization id to an **ACTIVE membership**. The organization is part of the URL; nothing about an "active organization" is stored in the session or the browser.

| Situation | Response |
|---|---|
| Not authenticated | `401` |
| Not a member, unknown organization, revoked member, malformed id | `404`, one constant body: indistinguishable from each other |
| Member of a **suspended** organization | `403` with code `ORGANIZATION_SUSPENDED` (checked only after membership is proven, so outsiders learn nothing) |
| Member who lacks a permission | `403` |
| Member who violates a rule (for example changing their own role) | `403` with a code |
| Conflicts (stale version, last owner, already a member) | `409` with a code |
| Rate limit | `429` with `Retry-After` |

Supporting measures:

- The membership is looked up before the organization, so existing and missing organizations cost the same.
- The gate **fails closed**: controllers receive the tenant through `@CurrentOrg`; a route that was somehow not gated fails with an error instead of running unguarded.
- Repositories for tenant data are always queried with the organization id (`findByIdAndOrganizationId`). An id from another tenant is simply "not found".
- Tenant tables reference memberships and departments with **composite foreign keys** `(organization_id, id)`, so the database refuses cross-tenant references even if application code is wrong. Two live examples, both tested with raw SQL that bypasses the application:
  - `fk_invitations_inviter`: an invitation's inviter must be a member of the **same** organization.
  - `fk_dm_department` and `fk_dm_membership` on `department_memberships`: a member of one organization cannot be assigned to a department of another, in either direction.
    Every later tenant table (requests and their history) follows the same pattern.
- Tests: a reusable fixture of two organizations and seven users, a standard isolation battery (`assertIsolated`) applied to every tenant endpoint, and an inventory test that fails if any `/api/orgs/{id}/...` route is reachable by a non-member. Schema tests also insert cross-tenant rows directly with SQL and assert that the foreign keys reject them.

## Authorization [built]

Roles belong to a **membership** (one per user per organization), so one person can be Owner of one organization and Employee of another. Permissions are an enum that lists which roles hold each one; this table is generated from `Permission.java` and tested for consistency (a higher role never has fewer permissions than a lower one).

| Permission | Owner | Admin | Manager | Employee |
|---|:-:|:-:|:-:|:-:|
| ORGANIZATION_VIEW, MEMBER_VIEW, DEPARTMENT_VIEW, REQUEST_CREATE | yes | yes | yes | yes |
| REQUEST_VIEW_ALL, REQUEST_REVIEW | yes | yes | yes | no |
| ORGANIZATION_UPDATE, MEMBER_INVITE, MEMBER_REVOKE, ROLE_ASSIGN, DEPARTMENT_MANAGE, AUDIT_VIEW | yes | yes | no | no |

Rules beyond the matrix (ADR-0007, ADR-0022):

- **Granting roles:** an Owner may grant any role; an Admin only Manager or Employee; nobody else grants anything. The same rule decides who may revoke or replace an invitation and who may change or remove a member: you may only touch roles you could grant yourself.
- Nobody changes or removes **themselves**; leaving is a separate action.
- An organization always keeps **at least one active Owner** (enforced in one place for role changes, removals and leaving).
- Every membership change takes a lock on the organization and re-reads the caller's own membership, because the gate's snapshot may be stale.
- Each member carries a version; a change request must send the version it saw (`409 STALE_VERSION` otherwise).
- Email addresses in the member list are returned only to roles that may invite members.

## Invitations [built]

- Tokens: 256-bit random, only the SHA-256 hash stored, 7-day expiry, single use. The raw token exists only in the email link and in the request body when it is used, never in a query string sent to the API.
- Accepting requires **both** possession of the token and a signed-in, verified account whose email equals the invited address. Every failure (wrong account, expired, replayed, revoked, already a member, suspended organization) returns the same generic `400`.
- Consumption is one conditional `UPDATE`, so replays and races cannot both succeed.
- One pending invitation per (organization, email), enforced by a partial unique index; inviting again replaces the pending one.
- The email is sent after the transaction commits, so it never refers to an invitation that was rolled back.
- The invited address is never written to the audit log.

## Audit log [built, partial]

- Events recorded in the **same transaction** as the change (the service refuses to run outside one), so a change and its record commit or roll back together.
- Each event type has an **allow-list** of metadata keys; unknown keys, null values and long values are rejected. Emails, tokens and passwords cannot reach the log.
- Events today: user email verified, organization created, invitation created, revoked, accepted and rejected, member role changed, member removed, member left.
- The table is append-only (triggers reject UPDATE, DELETE and TRUNCATE) and has no foreign keys, so history outlives what it describes.
- **Not built yet:** the endpoint and screen that let Owners and Admins read the log, request and approval events (Phase 6), and platform-level events.

## Error handling [built]

All errors are Problem Details (RFC 9457, `application/problem+json`), including `401`, `403`, `429` and CSRF failures produced by the security filters. Validation errors add an `errors` map from field name to messages; rule and conflict errors add a machine-readable `code`. Unexpected errors return a generic `500` with no stack trace or internal detail. Feature-specific exception handlers run before the global catch-all.

## Secrets and configuration [built]

- Configuration comes from environment variables; locally they are read from the git-ignored `.env` under the `local` profile. `.env.example` contains placeholders only.
- Compose binds PostgreSQL and Mailpit to `127.0.0.1`.

## Known limitations and remaining security work

Honest list, in rough priority order:

1. **Tenant isolation is proven for what exists** (organizations, members, invitations). Departments, requests, approvals and the dashboard must each pass the same harness when they are built.
2. **No platform administration yet.** An organization can only be suspended through the database; the platform endpoints (metadata only, audited) are planned.
3. **`Secure` cookie flag** must be enabled for the session cookie in every HTTPS deployment (Phase 9 checklist). The CSRF cookie gets no explicit `SameSite` attribute yet (browsers treat it as `Lax`).
4. **Missing response headers:** a `Referrer-Policy` is needed because invitation and verification tokens appear in page URLs; a Content-Security-Policy and a full header review are part of Phase 8.
5. **Rate limiter is in memory:** counters are per instance and reset on restart. Several instances need a shared store (ADR-0016).
6. **Session lifetime:** only an idle timeout, no absolute lifetime and no "sign out everywhere". A removed member's old sessions stay valid but the gate denies tenant access immediately.
7. **No password reset, password change, MFA, breached-password check or CAPTCHA.**
8. **Emails are fire-and-forget:** no retry or outbox. Inviting again or registering again sends a fresh link.
9. **Audit protection relies on database triggers;** a database owner or superuser can remove them. A restricted runtime database role is a Phase 9 task, and the local Docker user is a superuser.
10. **Distributed attacks** from many IPs against one account are not stopped by per-IP rules.
11. **Logging policy** (what may appear in logs, including addresses inside third-party error messages) is not formalized yet.
12. **OpenAPI exposure:** springdoc endpoints exist but are reachable only after authentication; the per-environment policy is undecided.
13. **No row-level security** yet; planned as defense in depth (Phase 8). **No Maven vulnerability scan** in CI yet.

Nothing here has had an external security review. This project should not be treated as production-ready.
