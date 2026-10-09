# ADR-0030: Dashboard

- **Status:** Accepted
- **Date:** 2026-10-10

## Context
The dashboard summarizes requests per organization. Its numbers must respect the same visibility rules as the request list, be internally consistent, and must not leak information a member could not see in the list.

## Decision
- **One endpoint** `GET /api/orgs/{orgId}/dashboard`, available to every member (ORGANIZATION_VIEW). It returns the scope of the figures (the whole organization for roles with REQUEST_VIEW_ALL, otherwise only the viewer's own requests), the total and a breakdown with an entry for every status (zero when empty), "awaiting review" (null for members who cannot review), the viewer's own requests, and the eight most recent workflow events.
- **Same rules as the list.** Employees only ever see counts and activity for requests they created. A test compares every dashboard figure with the matching list total for several viewers, including the sum of statuses and the "mine" and "awaiting review" figures.
- **Awaiting review** is the number of submitted requests created by someone else, which is what the viewer can actually decide on. The request list gets a matching `reviewable` filter (reviewers only; any other caller gets an empty page; combined with a different status it is empty), so the card and the list behind it always agree.
- **Consistency.** The service runs its queries in one read-only REPEATABLE READ transaction, so the figures come from a single snapshot.
- **Module boundary.** A `dashboard` module holds the use case and depends on `organization` and on the `request` module's `RequestStatistics` service, which owns the SQL over request tables. The dashboard module never reads those tables itself.
- **Recent activity** is the request history (submissions and decisions) of the requests the viewer may see, with actor name, action, reference and title, and no comments or email addresses. An index on `(organization_id, occurred_at DESC)` serves the query.
- **Frontend.** Each count links to the request list pre-filtered through URL parameters (`status`, `mine`, `reviewable`), read as starting values only. The dashboard is refetched every time the page opens (the figures change whenever anyone acts). The earlier foundation demo (a backend status badge) is removed from the UI.
- **Not included.** "Assigned to me" needs request assignment, which does not exist yet; "awaiting your review" is the actionable equivalent until it does.

## Alternatives considered
- Separate endpoints per widget: more round trips and no shared snapshot.
- Counting in the browser from the list: wrong for large lists and inconsistent with pagination.
- Caching the figures: unnecessary at this size, and stale counts next to a live list would be confusing.

## Consequences
- The recent-activity feed shows only workflow events; creating a draft does not appear until it is submitted.
- The recent-activity query for employees filters by creator after the time-ordered scan; fine at this scale, to be revisited if organizations grow very large.
- The list filters on the Requests page start from the link but are not written back to the URL (tracked as debt).
