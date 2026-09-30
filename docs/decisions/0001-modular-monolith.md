# ADR-0001: Modular monolith

- **Status:** Accepted
- **Date:** 2026-09-29

## Context
Nexus has several business capabilities (identity, organizations, memberships, departments, requests, approvals, audit, dashboard). It is built by one engineer and must be easy to run, test and reason about. Strong consistency is needed across capabilities, for example approving a request and writing its audit event in one transaction.

## Decision
Build a single deployable Spring Boot application organized as a **modular monolith**, with packages by business capability (`identity`, `organization`, `membership`, `department`, `request`, `audit`, `dashboard`, `shared`). Each module has `api` (controllers, DTOs), `application` (services, transactions), `domain` (business rules) and `persistence` layers where the separation adds value.

Modules interact through application-service interfaces, never through another module's repositories or entities. This rule is enforced by architecture tests (ArchUnit) from Phase 8.

## Alternatives considered
- **Microservices:** distributed transactions, deployment and observability overhead with no scaling need. Rejected.
- **Layered by technical type (controllers/services/repositories):** hides business boundaries and encourages cross-feature coupling. Rejected.

## Consequences
- One database transaction can span a business operation.
- Module boundaries stay clean enough to extract a service later if a real need appears.
- Discipline is required, because the compiler does not prevent cross-module access. Architecture tests provide the guardrail.
