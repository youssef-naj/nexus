# Nexus

A multi-tenant **Business Operations SaaS** platform. Independent organizations share one application and manage their members, departments, internal service requests, approval workflows and audit history, with **strict tenant isolation**: a user of Organization A can never access Organization B's private data.

> **Status: Phase 5 in progress (departments and service requests: backend done, screens next).** You can register, verify your email, create organizations, invite people, manage members and roles, and (through the API) manage departments, assign members to them, and create, edit and search service requests, with tenant isolation enforced and tested. Submitting and reviewing requests, the dashboard and the screens for departments and requests are **not implemented yet**. See [Status and roadmap](#status-and-roadmap) and [docs/status.md](docs/status.md).

## What exists today

- Registration with email verification (emails readable locally in Mailpit), login, logout, server-side sessions, CSRF protection, rate limiting.
- Organizations: create (you become Owner), list, switch between them (the organization is part of the URL), leave.
- A server-side **tenant gate**: outsiders get a `404` identical to "does not exist". Role-based permissions per membership.
- Invitations by email (hashed single-use tokens, accepted only by the invited verified account), member list, role changes, removal, with no role escalation, no self-management, and an organization always keeps an Owner.
- Departments (create, list, update, deactivate), assignment of members to departments, and internal service requests (reference numbers such as `REQ-000042`, drafts, editing by the creator, filtering, search, pagination). These are complete in the API and its tests; the screens are the next step.
- An append-only audit log written in the same transaction as each change.
- React + TypeScript frontend for all of the above, with loading, empty, validation and error states.
- Integration tests against a real PostgreSQL (Testcontainers), a reusable cross-tenant isolation test battery, frontend component tests, formatting and lint checks, GitHub Actions CI and Dependabot.
- Architecture decision records (ADRs) for the main design choices.

## Planned features (MVP)

Screens for departments and requests, submitting and reviewing requests (approve, reject, request changes), the audit history viewer, and an organization dashboard. See [docs/architecture.md](docs/architecture.md) and [docs/status.md](docs/status.md).

## Tech stack

| Area | Choice |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security, Spring Session (JDBC), Spring Data JPA (Hibernate), Flyway, Actuator, springdoc OpenAPI |
| Database | PostgreSQL 17 |
| Local email | Mailpit |
| Frontend | React, TypeScript (strict), Vite, React Router, TanStack Query, React Hook Form, Zod, Tailwind CSS, shadcn/ui |
| Testing | JUnit 5, Testcontainers, Vitest, Testing Library |
| Quality | Spotless (google-java-format, AOSP style), ESLint, Prettier, GitHub Actions, Dependabot |
| Local infrastructure | Docker Compose |

Architecture is a **modular monolith**, not microservices (see [ADR-0001](docs/decisions/0001-modular-monolith.md)).

## Prerequisites

- **JDK 21** (for example Eclipse Temurin), with `JAVA_HOME` set
- **Node.js 24 LTS** (20.19+ or 22.12+ also work) and npm
- **Docker Desktop** (with WSL 2 on Windows), running
- **Git**

Maven is not needed separately. The Maven Wrapper is included.

```powershell
java -version
node -v
docker --version
docker compose version
```

## Quick start (Windows PowerShell)

```powershell
git clone https://github.com/youssef-naj/nexus.git
cd nexus

# 1. Local configuration (never commit .env)
Copy-Item .env.example .env
notepad .env          # set POSTGRES_PASSWORD to any local value

# 2. Database and local mail server
docker compose up -d
docker compose ps     # postgres should report "healthy"

# 3. Backend (new terminal)
cd backend
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

# 4. Frontend (new terminal)
cd frontend
npm ci
npm run dev
```

Open <http://localhost:5173>. You land on the sign-in page.

<details>
<summary>macOS / Linux equivalents</summary>

```bash
cp .env.example .env
docker compose up -d
(cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local)
(cd frontend && npm ci && npm run dev)
```

</details>

### Try the main flow

1. On <http://localhost:5173>, choose **Create one** and register (password of at least 12 characters).
2. Open Mailpit at <http://localhost:8025>, open the verification email, click the link and **Confirm email**.
3. Sign in, then **Create an organization**. You become its Owner.
4. Open **Members** and invite a second email address as Manager. Register and verify that second account in a private window.
5. In Mailpit, open the invitation email, follow the link while signed in as the invited account and **Accept invitation**.
6. As Owner, change that member's role or remove them; as the member, notice which controls you no longer see.
7. Paste the first organization's URL while signed in as an unrelated account: you get the same "not found" screen as for a random id.

There are no pre-created demo accounts yet; demo data is planned.

### Useful URLs

| URL | Purpose |
|---|---|
| `http://localhost:5173` | Frontend |
| `http://localhost:8025` | Mailpit (read local emails) |
| `http://localhost:8080/api/system/ping` | Public ping |
| `http://localhost:8080/api/actuator/health` | Health check |

Every other API endpoint requires authentication.

### Configuration

Configuration comes from environment variables. Locally they are read from `.env` when the `local` Spring profile is active.

| Variable | Default | Meaning |
|---|---|---|
| `POSTGRES_DB` | `nexus` | Database name |
| `POSTGRES_USER` | `nexus` | Database user |
| `POSTGRES_PASSWORD` | none (required) | Database password |
| `POSTGRES_PORT` | `5432` | Host port for PostgreSQL |
| `POSTGRES_HOST` | `localhost` | Database host used by the backend |

No credentials are committed. `.env.example` contains placeholders only.

## Tests and checks

```powershell
# Backend: unit + integration tests (needs Docker) and formatting check
cd backend
.\mvnw.cmd verify
.\mvnw.cmd spotless:apply     # auto-format before committing

# Frontend
cd frontend
npm run format:check
npm run lint
npm run typecheck
npm run test
npm run build
```

CI runs the same commands for every push and pull request. The backend suite includes the cross-tenant isolation battery and a route-inventory test that fails if an organization-scoped route is reachable by a non-member.

## Troubleshooting

| Problem | Fix |
|---|---|
| `JAVA_HOME environment variable is not defined correctly` | Point `JAVA_HOME` at the JDK folder that contains `bin\javac.exe`, then open a new terminal. |
| Docker error: port 5432 not available, or Flyway says the schema is non-empty without a history table | Another PostgreSQL is using port 5432 (often a locally installed one), so the app talked to the wrong database. Set `POSTGRES_PORT=5433` in `.env`, run `docker compose up -d`, and restart the backend. |
| `Port 8080 was already in use` | An older copy of the backend is still running. Stop it from the IDE or terminal before starting another. |
| `password authentication failed` | The database volume was created with another password. Run `docker compose down -v` (deletes local data), then `docker compose up -d`. |
| `Failed to configure a DataSource` when running from IntelliJ | The Maven project was not imported (right-click `backend/pom.xml` and choose Add as Maven Project), or the working directory is not `backend`. |
| Sign-in returns `403` | The CSRF cookie is missing. Reload the page and retry. |
| `429 Too many attempts` | Rate limit reached. Wait for the time shown, or restart the backend to clear the in-memory counters. |

## Project structure

```
nexus/
├─ backend/     Spring Boot application (package com.l2c.nexus)
│               shared, identity, audit, organization, membership, team, department, request
├─ frontend/    React + TypeScript application
├─ docs/        Architecture, data model, security, status, decision records
├─ infra/       Reserved for deployment assets
├─ .github/     CI workflow and Dependabot configuration
├─ compose.yaml Local PostgreSQL and Mailpit
└─ .env.example Configuration template
```

## Documentation

- [Architecture](docs/architecture.md)
- [Data model](docs/data-model.md)
- [Security](docs/security.md)
- [MVP status against the acceptance criteria](docs/status.md)
- [Architecture decision records](docs/decisions/README.md)

## Status and roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Requirements and architecture | Done |
| 1 | Development foundation, CI, first vertical slice | Done |
| 2 | Identity and authentication | Done |
| 3 | Organizations and tenant isolation | Done |
| 4 | Memberships, invitations, roles | Done |
| 5 | Departments and service requests | In progress (backend done) |
| 6 | Approval workflow and audit history | Planned |
| 7 | React dashboard and user experience | Planned |
| 8 | Security hardening and testing | Planned |
| 9 | Deployment, documentation, demonstration | Planned |

## Known limitations

- No screens for departments or requests yet, no way to submit or review a request, no dashboard; no platform administration; organization settings are not editable.
- No password reset, MFA, or "sign out everywhere". Rate limiting is per application instance and in memory.
- No demo accounts, screenshots or deployment guide yet.
- See [docs/security.md](docs/security.md) for the full, honest list of remaining security work.
- Not production-ready. Nothing here has been security-reviewed or deployed.

## License

[MIT](LICENSE)
