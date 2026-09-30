# ADR-0012: Audit log design

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
Security and business events must be traceable, and the trail must be trustworthy.

## Decision
- An `audit_logs` table records: event ID, optional organization ID (null for platform events), actor user ID, event type, target type and ID, `metadata` (JSONB), and UTC timestamp.
- The audit row is written **in the same transaction** as the business change, so an event cannot exist without the change or the reverse.
- **Append-only:** the application's database role has INSERT and SELECT on `audit_logs` but not UPDATE or DELETE. Retention and archival happen through a separate, controlled process.
- **No hard foreign keys**, so audit history survives deletion or deactivation of what it describes.
- **Metadata is built from an allow-list** of safe fields per event type. Passwords, tokens, invitation secrets and unnecessary personal data are never stored. IP addresses are excluded from the MVP because they are personal data, and this can be revisited.
- **Events in scope:** organization creation; invitation created, accepted, rejected, revoked; membership added, revoked; role changed; request submitted, approved, rejected, changes requested; sensitive organization setting changes; platform actions.
- **Who can view:** Owners and Admins of the organization see that organization's events. Platform administrators see only platform-scoped events.
- Per-request business history lives separately in `request_events` (ADR-0010) and is visible to anyone who may view the request.

## Alternatives considered
- Application logs as the audit trail: not queryable, not durable, mixed with noise.
- Database triggers on every table: capture too much and lack business meaning.

## Consequences
Every sensitive use case must call the audit service, so tests assert the expected audit row. The privilege split must be reflected in the deployment setup (migration role versus runtime role).
