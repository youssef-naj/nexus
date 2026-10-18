# ADR-0036: End-to-end and accessibility testing

- **Status:** Accepted
- **Date:** 2026-10-18

## Context
Unit, integration and component tests prove each layer. They cannot prove that the layers agree in a real browser, that keyboard users can operate the screens, or that the production images behave like the tested code.

## Decision
- **Playwright tests drive the seeded, production-shaped stack** in Chromium: the main journeys as real people (a request moving from draft to approved through four people with every step in its history; departments from creation to deactivation by keyboard; sign-up, email verification and sign-in), the access rules (employees see only their own requests, organizations are isolated even for a person who belongs to both, nobody reviews their own request, a manager is refused the audit log, two reviewers deciding at once), the platform operator suspending and reactivating an organization, keyboard behavior (skip link, focus moving to the content after navigation, page titles, tab order, visible focus), and reflow at 390, 640 and 768 px.
- **One login per person per run.** A global setup signs each demo account in through the API and saves the session; multi-person tests use one browser context per person. Tests create uniquely named data and never edit the seeded story; tests that change shared state clean up through an independent channel (the API), not through the page they may have lost.
- **Accessibility gate.** Axe scans every main screen as each kind of person, on a desktop and a phone width, and fails on any WCAG 2.0, 2.1 or 2.2 level A or AA violation. Axe's best-practice findings are listed as annotations without failing the run. What axe cannot check is covered by the explicit keyboard, focus and reflow tests.
- **CI** runs everything against freshly built images on every push.

## What it found
The first run found three real defects that no earlier layer could: an explanation lost when a conflict removed the panel that displayed it (rule adopted: results are reported to the page, never held by a component that can disappear); a contrast failure in the destructive color; and a table scroller keyboard users could not reach. All three were fixed and now have tests.

## Alternatives considered
- Mocking the API in browser tests: faster, but it would not exercise the proxy, the headers, the cookies or the real workflow.
- Several browsers: valuable later; one browser keeps the suite fast and the failures attributable.

## Consequences
- Automated accessibility checks find only part of the problems; there has been no screen-reader or user testing.
- The suite depends on the seeded story; changing the seed means changing the tests.
- The tests add data to a demo stack on every run; resetting the stack gives a clean demo.
