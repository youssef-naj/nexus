# ADR-0002: Technology stack and version policy

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
The project must use a modern, maintainable, mainstream stack with compatible dependency versions and no deprecated APIs.

## Decision
**Backend:** Java 21 LTS, Spring Boot 4.1.x (Spring Framework 7, Spring Security 7, Hibernate 7), Maven with the Maven Wrapper, Spring Web, Spring Security, Spring Data JPA, Jakarta Bean Validation, PostgreSQL, Flyway, Actuator, springdoc OpenAPI (a release compatible with Spring Boot 4), JUnit 5, Mockito, Testcontainers.

**Frontend:** React, TypeScript (strict), Vite, React Router, TanStack Query, React Hook Form with Zod, Tailwind CSS with shadcn/ui, Vitest with Testing Library, ESLint and Prettier. Playwright is added after the MVP workflow is stable.

**Infrastructure:** Docker and Docker Compose, GitHub Actions, Mailpit for local email.

**Version policy**
1. Exact versions are pinned in `pom.xml`, `package-lock.json` and `compose.yaml`, not in this document.
2. Prefer the versions managed by the Spring Boot BOM. Override only with a documented reason.
3. Spring Boot 4.1 was chosen over 4.0 because its open-source support window is longer.
4. Dependencies are added only when they replace meaningful custom code.
5. Dependabot and a CI dependency scan track updates and known vulnerabilities.

## Alternatives considered
- Java 25 LTS: newer, but the project requirement is Java 21 and Spring Boot supports it fully. May be revisited later.
- Spring Boot 3.5: open-source support ends in June 2026. Rejected for a new project.

## Consequences
Modern APIs, but some third-party libraries lag behind Spring Boot majors. Each new dependency must be checked for compatibility before adoption.
