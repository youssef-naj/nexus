# ADR-0029: Workflow screens and the audit viewer

- **Status:** Accepted
- **Date:** 2026-10-09

## Context
The approval workflow needs screens, and the audit log, until now write-only, needs a way for authorized people to read it, without turning it into a place where personal data or review comments leak.

## Decision
- **Workflow UI.** The request detail page shows a submit or review panel built from the `actions` list the server returns for the current viewer; unknown actions are ignored. Every action sends the version the page loaded. Rejecting and requesting changes require a comment, checked in the browser and again by the server; its field errors appear under the comment box. Conflicts (stale version, already handled) reload the data and explain what happened. The history below the request lists each transition with actor, time, status change and comment, rendered as plain text.
- **Audit viewer API.** `GET /api/orgs/{orgId}/audit` returns the organization's events newest first, paginated (size capped at 100), filterable by event type and by inclusive UTC calendar days. It requires AUDIT_VIEW (Owner, Admin); other members get 403, non-members 404. The query is always scoped by organization, so platform-level events never appear. Metadata is the allow-listed set only, expanded from the stored jsonb by PostgreSQL. The actor's display name is read with a join over users (the documented read-model exception). The response contains no email addresses and no review comments.
- **Where it lives.** In the `team` module (organization administration). Putting it in `audit` would create a dependency cycle, since `organization` already depends on `audit` to write events.
- **Audit screen.** An Audit tab, visible only with AUDIT_VIEW, shows when, what, who and a short summary of the metadata, with event-type and date filters. Identifiers are never displayed; unknown event types are shown humanized.

## Alternatives considered
- Showing identifiers and raw metadata for completeness: noisy and risks exposing more than needed.
- Letting Managers read the log: audit is an administrative control, so it follows the AUDIT_VIEW permission defined in ADR-0007.
- Parsing the jsonb in Java: adds a JSON dependency path for no gain.

## Consequences
- Operations on other tenants' data cannot appear here by construction; platform-level events need their own viewer with platform administration.
- Dates are UTC calendar days, so late-evening events in other time zones may fall on the next day in filters.
- The log offers no export, search by text, or filter by actor yet.
