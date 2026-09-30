# ADR-0013: Frontend architecture

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
The UI must be professional, accessible, responsive and maintainable, while all security remains on the server.

## Decision
- **Toolchain:** React and TypeScript in strict mode (plus `noUncheckedIndexedAccess`), built with Vite, in `frontend/`.
- **Structure:** feature-oriented.
  - `src/app`: providers, router, layout shell.
  - `src/features/<name>`: pages, components, hooks, API functions and schemas for one capability.
  - `src/shared`: API client, generic UI helpers, hooks, types.
  - `src/components/ui`: shadcn/ui components (generated code that we own).
- **Server state:** TanStack Query. **Local UI state:** component state. No global store unless a concrete need appears.
- **Forms:** React Hook Form with Zod schemas. Server validation errors from the problem body are mapped onto fields.
- **Routing:** React Router. The organization ID is part of the route (`/orgs/:orgId/...`), mirroring ADR-0004.
- **API client:** one `apiFetch` wrapper that uses same-origin cookies, adds the CSRF header for unsafe methods, parses problem-detail errors into a typed `ApiError`, and centralizes handling of 401 (redirect to login) and 5xx errors. API responses are validated with Zod at the boundary where practical.
- **Styling and components:** Tailwind CSS with shadcn/ui (Radix primitives), which provides accessible components whose code lives in the repository.
- **Security stance:** route guards and hidden buttons are usability only. No tokens in browser storage. No `dangerouslySetInnerHTML` with user content.
- **Quality gates:** ESLint, Prettier, `tsc` typecheck, Vitest with Testing Library, and later a few Playwright tests. All run in CI.
- **Development proxy:** Vite proxies `/api` to the backend so the browser sees one origin.

## Alternatives considered
- MUI or Mantine: fine libraries, but heavier and less transparent to customize than owned shadcn components.
- Redux or Zustand for server data: duplicates what TanStack Query already provides.
- Next.js: server rendering is unnecessary for an authenticated internal app and would blur the backend/frontend boundary.

## Consequences
We maintain the generated component code, but gain full control and accessibility defaults. Feature folders keep changes local.
