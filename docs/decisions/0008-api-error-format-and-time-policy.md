# ADR-0008: API error format and time policy

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
Clients need one predictable error shape. Time handling bugs are common in multi-user systems.

## Decision
**Errors.** All errors use Problem Details (RFC 9457) with `application/problem+json`, via Spring's `ProblemDetail`. Fields: `type`, `title`, `status`, `detail`, `instance`. Validation failures add an `errors` extension: a map from field name to a list of messages. Messages never contain stack traces, SQL, internal identifiers or secrets. A `traceId` is included for correlation with logs.

**Time.**
- All instants are stored in `timestamptz` and handled in Java as `Instant`.
- The API serializes ISO-8601 UTC (`2026-09-29T10:15:30Z`).
- Calendar dates without a time (for example a due date) use `LocalDate` and `date`.
- Time is obtained from an injected `Clock` bean so tests can control it.
- The frontend converts to the user's local time only for display.

## Alternatives considered
A custom error envelope: no ecosystem benefit over an open standard.

## Consequences
Uniform client-side error handling. All exception mapping is centralized in one `@RestControllerAdvice`.
