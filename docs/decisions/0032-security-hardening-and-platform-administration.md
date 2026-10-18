# ADR-0032: Security hardening and platform administration

- **Status:** Accepted
- **Date:** 2026-10-12

## Context
Phases 2 to 7 built the application's functional security: sessions, CSRF, tenant isolation, roles. Phase 8 closes the gaps around it: what the HTTP layer guarantees by itself, what the build refuses to ship, and the one capability that sits outside every tenant, platform administration (ADR-0006). The two parts were built together and the code refers to this record for both.

## Decision

### A. HTTP hardening, architecture rules and dependency scanning
- **Response headers.** Every API response, errors included, carries: `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'`, `Referrer-Policy: no-referrer`, a restrictive `Permissions-Policy`, `Cross-Origin-Opener-Policy: same-origin`, `Cross-Origin-Resource-Policy: same-origin`, and Spring Security's defaults (`nosniff`, `X-Frame-Options: DENY`, no caching). HSTS is not sent over plain HTTP; the TLS layer in front of the stack sends it. The SPA's own policy is set by its web server (ADR-0034).
- **Cookies.** The CSRF cookie is readable by JavaScript on purpose (the SPA copies it into a header) but is `SameSite=Lax`, and `Secure` when `nexus.security.secure-cookies` is on. The session cookie is `HttpOnly` and `SameSite=Lax`; the `prod` profile makes both `Secure` from one switch, `NEXUS_SECURE_COOKIES`, which defaults to true. Session ids never travel in URLs.
- **Size limits.** Request bodies are limited to 100 KB (64 KB for form posts). The limit is the first filter in the chain: a declared length above it is answered with `413` before anything is read, and bodies of unknown length are counted as they are read. Oversized input never reaches parsing, CSRF handling or password hashing.
- **API documentation is off by default.** Swagger UI and the OpenAPI document are not exposed; the contract is published deliberately (`docs/api-routes.md`, generated from the code).
- **Architecture tests** (ArchUnit) run in `verify`: no cycles between modules; each module may use only the modules it is allowed to; application code never depends on the web layer; domain code depends on no other layer; controllers live in `api` packages and never touch entities or repositories; persistence classes and entities are private to their module; tenant repositories are never used by id alone (only through methods that take the organization); only the platform module may change an organization's status; no standard streams, `java.util.logging`, field injection or generic exceptions; no throwable is passed straight to a logger (ADR-0033).
- **Dependency scanning.** OSV-Scanner reads the Maven pom (including transitive dependencies) and the npm lockfiles (frontend, end-to-end tests) on every push and pull request and weekly, and fails on a known vulnerability. Policy: no known-vulnerable runtime dependency ships; a dev-only finding with no published fix may carry a dated exception (`frontend/osv-scanner.toml`) that expires and forces a new decision. The first scan found critical issues in the embedded web server and in Jackson; they were fixed by upgrading.

### B. Platform administration
- **Who.** Platform administrators are declared in configuration (`nexus.platform.admin-emails`, at most 20 addresses). At startup the application grants the role to listed accounts that are active and email-verified and revokes it from everyone else. No endpoint grants the role, so nobody can promote themselves or anyone else through the application. Each grant and revoke is audited, with no actor and no personal data.
- **Checked every time.** The role is read from the database on every request to `/api/platform/**`, never cached in the session: removing someone from the list, disabling their account or demoting them takes effect on their next request.
- **Invisible to everyone else.** Unauthenticated callers get `401`; every other caller, organization owners included, gets a `404` identical to the tenant gate's, so the platform API's existence is not revealed.
- **Metadata only (ADR-0006).** The platform sees an organization's name, slug, status, creation date and active member count. It never sees members' names or emails, requests, departments or comments. Holding the platform role grants no tenant permission at all; a test proves it with an administrator who is also an ordinary member.
- **Suspend and reactivate.** The only platform actions on tenants. Each needs a reason from a fixed list (suspension: policy violation, security concern, non-payment, customer request, other; reactivation: issue resolved, suspended in error, customer request, other), so nothing a person types can reach the audit log. Each carries the version the administrator saw (`409` if stale), takes the exclusive organization lock (so it waits for decisions in flight), and is idempotent. Suspended organizations answer their members with `403 ORGANIZATION_SUSPENDED`, cannot accept invitations, and stay in members' organization lists with a badge.
- **Two audit records per action, in one transaction.** A platform-level record (administrator, organization, reason; no organization id) visible only in the platform audit view, and a tenant-level record (organization, "System" as actor, reason) that the organization's Owners and Admins can read without learning who acted.
- **Platform audit view.** Lists only `PLATFORM_*` events that belong to no organization, so the platform cannot browse what people did inside tenants.

## Alternatives considered
- Granting the platform role through an API or an admin screen: needs a bootstrap secret and creates a self-promotion attack surface.
- Free-text reasons: convenient, but personal data would end up in the audit log.
- Caching the role in the session: simpler, but a revoked administrator would keep access until their session ended.
- Publishing OpenAPI/Swagger for convenience: an unreviewed surface; a generated route reference gives readers the contract without it.

## Consequences
- Changing the administrator list needs a restart (revocation included).
- There is no multi-factor authentication for platform administrators and no separate rate limit on platform actions (see `docs/status.md`).
- The architecture rules will fail the build when someone breaks a boundary, which is the point; a justified exception is an explicit change to the rule and to this record.
