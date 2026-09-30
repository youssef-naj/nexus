# ADR-0003: Session-cookie authentication

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
Nexus is a browser-based SPA with its own user accounts. We need secure authentication, real logout and revocation, and a setup that runs locally without external identity services.

## Decision
Use **Spring Security first-party authentication with server-side sessions**:
- Passwords hashed with `DelegatingPasswordEncoder` (bcrypt by default), so the algorithm can be upgraded without a flag-day migration.
- Sessions stored in PostgreSQL through Spring Session JDBC, so sessions can be revoked and survive restarts.
- Session cookie is `HttpOnly`, `Secure` outside local development, `SameSite=Lax`. The session ID is rotated at login.
- CSRF protection stays **enabled**, using a cookie-to-header token (`XSRF-TOKEN` cookie, `X-XSRF-TOKEN` header) for the SPA.
- The SPA and the API share one origin through a reverse proxy (Vite dev proxy locally), so CORS is not needed for normal operation.
- Rate limiting on login, registration and invitation acceptance. Login errors are generic and do not reveal whether an email exists.
- No authentication tokens in `localStorage` or `sessionStorage`.

## Alternatives considered
- **OIDC with Keycloak:** enterprise-realistic but heavy to run locally. Kept as a documented migration path.
- **JWT in localStorage:** exposed to XSS and hard to revoke. Rejected.
- **JWT in HttpOnly cookie:** possible, but adds revocation problems that server-side sessions already solve.

## Consequences
- We own credential storage, email verification and abuse protection (see ADR-0011).
- Horizontal scaling works because sessions live in the database.
- Session lookups add a database read per request, acceptable for this scale.
