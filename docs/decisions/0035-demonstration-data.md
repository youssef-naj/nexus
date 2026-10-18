# ADR-0035: Demonstration data

- **Status:** Accepted
- **Date:** 2026-10-17

## Context
A reviewer needs to see a lived-in system in minutes, and screenshots and end-to-end tests need known data. Seeding must not weaken the production image.

## Decision
- **The seed is a script that uses the public API and the demo mail catcher** (`scripts/seed-demo.mjs`), not application code. Accounts register, read their verification email, sign in, create organizations, invite and accept, and requests move through the real workflow. Nothing in the production image can create accounts with a known password, and any bug in those paths shows up while seeding.
- **Fictional people on a reserved example domain** (`@demo.example`), a single documented demo password that is explicitly for the demo only, and a platform operator account whose role is granted by listing it in the demo configuration.
- **The data tells a story.** Two organizations (one person belongs to both, so the switcher has something to show); every role; departments including an inactive one; twelve requests covering every state: drafts, awaiting review, assigned, sent back and resubmitted, approved with comments, rejected.
- **Safe to re-run, not a repair tool.** If the first account can already sign in and owns the first organization, the script stops. A failed run is fixed by resetting the stack, because registration is deliberately enumeration-safe and existing accounts receive no second verification email.
- **Screenshots** come from a Playwright capture project that signs in through the API and photographs the pages; they are regenerated with one command.

## Alternatives considered
- A seed profile or migration with fixed accounts: simpler, but ships known credentials in the production artifact.
- SQL fixtures: bypass password hashing, the workflow and the audit log, so the demo would not prove the real behavior.

## Consequences
- The demo is a demo: plain HTTP, a shared password, no real mail. `.env.demo` is never reused for a deployment.
- Rate limits can slow the script; it waits and retries, and a clean restart of the backend resets them.
