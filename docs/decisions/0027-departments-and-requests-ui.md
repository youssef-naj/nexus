# ADR-0027: Departments and requests UI

- **Status:** Accepted
- **Date:** 2026-10-07

## Context
The screens for departments and service requests must follow the server's rules without duplicating them, stay accessible, and render user-supplied text safely.

## Decision
- Routes live under the organization: `/orgs/:orgId/departments`, `/departments/:id`, `/requests`, `/requests/new`, `/requests/:id`, `/requests/:id/edit`. Navigation entries appear according to permissions (hints only).
- The request detail carries an `editable` flag computed by the server for the current viewer (creator, and a draft or sent-back request). The UI never derives edit rights itself.
- Edits send the version the page loaded. Conflicts (`STALE_VERSION`, `CONCURRENT_MODIFICATION`) reload the data and show an explanation. Server rule codes become sentences in one message function per feature; field errors from the server appear under their fields.
- Filters, sorting and search are sent as query parameters built by one helper that skips empty values and URL-encodes the rest. The list state is kept in component state (not the URL) for now.
- Department pickers show active departments, plus a request's current department even if it has since been deactivated.
- User-supplied text (names, titles, descriptions) is rendered only as React text; there is no raw HTML rendering anywhere. Descriptions use `white-space: pre-wrap` to keep line breaks.
- Dates: due dates are calendar dates shown without time zone conversion; timestamps are shown in the viewer's locale.
- Confirmations (deactivate, remove) are inline steps; role and filter pickers are native selects (ADR-0023).
- Shared building blocks: a pagination bar, a textarea field, a query-string helper, a page-envelope schema and common error wording.

## Alternatives considered
- Deriving "can edit" on the client from role and status: duplicates the server's rules and drifts.
- Persisting filters in the URL now: better for sharing, but more routing code than this phase needs.
- A rich-text description: would require sanitization and is out of scope.

## Consequences
- Filters reset when leaving a list page.
- The department member picker loads the first 100 organization members; larger organizations will need a searchable picker.
- The members page still carries its own pagination controls and control-character check; consolidating onto the shared pieces is tracked as debt.
