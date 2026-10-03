# ADR-0016: Rate limiting and abuse protection

- **Status:** Accepted
- **Date:** 2026-10-01

## Context
Public authentication endpoints are targets for password guessing, credential stuffing, registration floods and email bombing.

## Decision
- A first-in-chain filter rejects abusive requests with 429 and a Retry-After header before CSRF checks or password hashing.
- Login: every attempt counts against the client IP (30 per 5 minutes). Only failed attempts count against an (email, IP) pair (5 per 15 minutes); a success clears that pair. A blocked pair stays blocked even with the correct password.
- Registration: 10 per IP per hour. Email verification: 30 per IP per 10 minutes.
- Emails are limited to 3 per address per hour. Beyond that nothing is sent and the response is unchanged, to avoid revealing anything about the address (ADR-0014).
- The limiter is an in-memory sliding-window log with a bounded number of keys and hits, purged every minute. It fails closed when full. Limits are configurable under `nexus.rate-limit.*`.
- The client IP is `getRemoteAddr()`. Behind a reverse proxy, `server.forward-headers-strategy` is enabled for trusted proxies only; `X-Forwarded-For` is never parsed by hand.

## Alternatives considered
- Blocking by email alone: lets anyone lock a victim out. Rejected.
- A library such as Bucket4j: capable, but an extra dependency for rules this small. A shared store (Redis or the database) becomes worthwhile when running several instances.

## Consequences
- Counters are per instance and are lost on restart.
- Users behind one shared IP (an office NAT) share the per-IP budgets.
- A distributed attack from many IPs against one account is not stopped. That needs a CAPTCHA, a WAF, or breach-password checks, which are listed as future hardening.
- Limits must be re-tuned when real traffic is known.