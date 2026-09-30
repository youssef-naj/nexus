# Nexus

A multi-tenant **Business Operations SaaS** platform. Independent organizations share one application and manage their members, departments, internal service requests, approval workflows and audit history, with **strict tenant isolation**: a user of Organization A can never access Organization B's private data.

> **Status: Phase 1 complete (development foundation).** The project builds, migrates its database, runs its tests in CI, and serves one end-to-end slice. Business features (authentication, organizations, requests, approvals) are **not implemented yet**. See [Status and roadmap](#status-and-roadmap).

## What exists today

- Spring Boot backend with PostgreSQL, Flyway migrations, deny-by-default security, a health endpoint and a ping endpoint.
- React + TypeScript frontend that calls the backend through a dev proxy and shows its status.
- Integration tests against a real PostgreSQL (Testcontainers), frontend unit tests, formatting and lint checks.
- GitHub Actions CI and Dependabot.
- Architecture decision records (ADRs) for the main design choices.

## Planned features (MVP)

Identity and authentication, organizations and memberships with role-based access, invitations, departments, internal service requests, an approval workflow (draft, submit, approve, reject, request changes), audit history, and an organization dashboard. See [docs/architecture.md](docs/architecture.md) and [docs/decisions](docs/decisions/README.md).

## Tech stack

| Area | Choice |
|---|---|
| Backend | Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA (Hibernate), Flyway, Actuator, springdoc OpenAPI |
| Database | PostgreSQL 17 |
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

Check your tools (PowerShell):

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

# 2. Database
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

Open <http://localhost:5173>. You should see the Nexus card with a **Backend ok** badge.

<details>
<summary>macOS / Linux equivalents</summary>

```bash
cp .env.example .env
docker compose up -d
(cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=local)
(cd frontend && npm ci && npm run dev)
```

</details>

### Useful endpoints

| URL | Purpose |
|---|---|
| `http://localhost:5173` | Frontend |
| `http://localhost:8080/api/system/ping` | Public ping |
| `http://localhost:8080/api/actuator/health` | Health check |

Every other endpoint currently returns `401` (deny by default).

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

CI runs the same commands for every push and pull request.

## Project structure

```
nexus/
├─ backend/     Spring Boot application (package com.l2c.nexus)
├─ frontend/    React + TypeScript application
├─ docs/        Architecture, data model, decision records
├─ infra/       Reserved for deployment assets
├─ .github/     CI workflow and Dependabot configuration
├─ compose.yaml Local PostgreSQL
└─ .env.example Configuration template
```

## Documentation

- [Architecture](docs/architecture.md)
- [Data model](docs/data-model.md)
- [Architecture decision records](docs/decisions/README.md)

## Status and roadmap

| Phase | Scope | Status |
|---|---|---|
| 0 | Requirements and architecture | Done |
| 1 | Development foundation, CI, first vertical slice | Done |
| 2 | Identity and authentication | Next |
| 3 | Organizations and tenant isolation | Planned |
| 4 | Memberships, invitations, roles | Planned |
| 5 | Departments and service requests | Planned |
| 6 | Approval workflow and audit history | Planned |
| 7 | React dashboard and user experience | Planned |
| 8 | Security hardening and testing | Planned |
| 9 | Deployment, documentation, demonstration | Planned |

## Known limitations

- No authentication or business features yet. The API is deny-by-default except ping and health.
- Swagger UI and `/v3/api-docs` are enabled by the springdoc default but are not reachable without authentication. Their exposure policy will be defined per profile.
- No demo accounts, seed data, screenshots or deployment guide yet. They arrive with the phases that need them.
- Not production-ready. Nothing here has been security-reviewed or deployed.

## License

To be decided. Until a `LICENSE` file is added, all rights are reserved.
