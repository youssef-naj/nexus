# ADR-0034: Deployment

- **Status:** Accepted
- **Date:** 2026-10-17

## Context
The application must be deployable as it was built and tested, without choosing a hosting provider, and a reviewer must be able to run the same stack locally in a few commands.

## Decision
- **One origin.** An nginx container serves the built single-page app and proxies `/api/` to the backend, so the browser talks to one origin and CORS does not exist.
- **The SPA's Content-Security-Policy lives in nginx**: scripts from the page's own origin only (no inline code, no `eval`), `frame-ancestors 'none'`, and the other protective headers on every response including errors. `style-src` still allows inline styles; tightening it is tracked in `docs/status.md`. Headers are repeated per location by include, because nginx drops inherited `add_header` lines as soon as a location declares its own.
- **Caching.** The page is never cached; hashed asset files are cached for a year as immutable; a missing asset is a `404`, not the app.
- **Internals are not reachable.** `/api/actuator/**` is a `404` through the proxy; the container health checks talk to the backend directly. The backend and the database publish no ports; the database sits on an internal network only the backend shares. The web container binds to `127.0.0.1` unless the operator chooses otherwise, and a TLS proxy is expected in front.
- **Containers.** Multi-stage builds; non-root users; read-only filesystems with a small `tmpfs`; all capabilities dropped; `no-new-privileges`; memory limits; health checks; graceful stop periods.
- **Configuration.** Everything environment-specific comes from environment variables (`.env.prod.example`); no default secrets (compose refuses to start without a database password, public URL, mail host and sender). Secure cookies are the default and can only be switched off through one explicit variable, used by the plain-HTTP demo file.
- **Client addresses.** nginx overwrites `X-Forwarded-For` with the address it sees and trusts a forwarded address only from private networks; the backend trusts the proxy's header the same way. Per-IP rate limits therefore see real clients.
- **Separate stacks.** The deployable stack has its own compose project name and the demo uses different ports from development, so the two never share containers or volumes.
- **Gate.** CI builds the images from scratch, starts the stack, and runs a smoke test (static site, headers, proxy rules, one real user journey) followed by the end-to-end tests (ADR-0036). Images are built in CI but not pushed.
- **Operations.** Database schema changes are forward-only Flyway migrations applied when the backend starts; backups are `pg_dump` of the data volume (`docs/deployment.md`).

## Alternatives considered
- A hosting-specific setup (a platform service or Kubernetes): ties the project to a provider and hides the moving parts.
- Serving the SPA from Spring Boot: fewer containers, but the web server's headers and caching rules are better expressed in a web server.
- Publishing images from CI: needs a registry and credentials, with no benefit for a project without a running production.

## Consequences
- TLS, HSTS and public exposure are the operator's responsibility (a minimal proxy example is in `docs/deployment.md`).
- Single instance only: rate limits and rate-limit state are in memory (tracked in `docs/status.md`).
- No automated backups or image vulnerability scanning yet (tracked).
