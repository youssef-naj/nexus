# ADR-0033: Logging policy and failure behavior

- **Status:** Accepted
- **Date:** 2026-10-16

## Context
Logs and error responses are where personal data and internals leak by accident, and outages are where behavior is rarely designed. The system must say what it writes to its logs, answer failures safely, and behave predictably when the database or the mail server is down.

## Decision
- **Logging policy.** Logs never contain email addresses, passwords, tokens or request contents, at any level. Random identifiers (user ids, incident ids) are allowed. The policy is enforced by tests that run real flows with debug logging on (registration, verification, sign-in and sign-out, duplicate and unverified accounts, failed logins, bad tokens, malformed JSON, a real database constraint violation) and then search everything that was logged for the values used.
- **Exceptions are logged by description, not by message.** `SafeExceptionFormatter` writes class names, the SQLSTATE code and the top stack frames, never messages: messages carry data (PostgreSQL's duplicate-key error contains the email). An architecture rule forbids passing a throwable to a logger. Hibernate's SQL-error logger (`org.hibernate.orm.jdbc.error`, plus its older name) is switched off because it prints exactly those details, and the health indicator's logger is quieted so a probe during an outage does not print a stack trace each time.
- **Incident references.** Every unexpected failure returns a generic Problem Details body with a short `incidentId`; the log line carries the same id, so support can correlate without exposing internals.
- **Outages are `503`, not `401` or a hang.** A classifier recognizes database connection failures however they are wrapped. A filter right after the security-headers filter handles failures outside controllers (session loading, authentication, account checks); the controller advice uses the same classifier. Responses carry `Retry-After` and the security headers. A login or registration during an outage is `503`, never "wrong password", and leaves nothing half-saved.
- **Health.** Readiness includes the database, so an orchestrator stops sending traffic during an outage; liveness does not, so a healthy process is not restarted for a database problem.
- **Timeouts everywhere that could hang.** Five seconds to obtain a database connection, fifteen seconds per `JdbcTemplate`/`JdbcClient` query, five seconds per SMTP step. JPA repository queries do not yet have a global timeout (tracked in `docs/status.md`).
- **Mail is best-effort.** Emails are sent asynchronously after the transaction commits. When the mail queue is full, surplus emails are dropped with a warning instead of throwing into the request that published them, so registration cannot fail because the mail queue is busy. Failures are logged safely and not retried.
- **Graceful shutdown.** In-flight requests and queued emails finish before exit, bounded by 20 seconds.
- **Frontend.** A `503` is described as a temporary outage, never as a sign-out. The sign-in check keeps the session it already has when a background refresh fails, so a half-typed form is not discarded.

## Alternatives considered
- Logging full stack traces: invaluable for debugging, but messages inside them contain personal data.
- A redacting log filter based on patterns: unreliable, and a miss is invisible.
- Retries and circuit breakers: unnecessary at this scale and they hide the failure from the user.

## Consequences
- Debugging relies on the incident id, the exception class, the SQLSTATE and the top frames, not on messages.
- Failed emails are not retried; a user can register again to receive a new link.
- Logs are plain structured lines; aggregation and alerting are out of scope (tracked in `docs/status.md`).
