# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project Overview

Internal append-only Audit Log service for compliance, security, and observability. Consumers include Compliance Officers, SRE, and Security Analysts.

## Tech Stack

- Java 21, Spring Boot 3, Gradle (Kotlin DSL)
- PostgreSQL with Flyway migrations
- Testcontainers for integration tests

## Build & Test Commands

```bash
./gradlew build                                              # compile + test
./gradlew test                                               # run all tests
./gradlew test --tests "com.auditlog.SomeTest"               # single test class
./gradlew test --tests "com.auditlog.SomeTest.methodName"    # single test method
./gradlew bootRun                                            # run locally (default profile)
./gradlew bootRun --args='--spring.profiles.active=local'   # run with local profile
```

## Architecture

Base package: `com.auditlog`. Five layers, each in its own package:

| Package | Role                                                                                                 |
|---|------------------------------------------------------------------------------------------------------|
| `controller` | REST endpoints, input validation via `@Valid`, delegates to facade                                   |
| `facade` | Interface + `impl/` implementation, Orchestration — coordinates services, maps DTOs; keep logic-free |
| `service` | Interface + `impl/` implementation; owns business rules and transactions                             |
| `dao/entity` | JPA entities (`@Column(updatable = false)` enforces append-only at ORM level)                        |
| `dao/repository` | Spring Data JPA repositories                                                                         |
| `dto/request` | Incoming request records with Bean Validation annotations                                            |
| `dto/response` | Outgoing response records with a static `from(entity)` factory                                       |

Entity→DTO mapping is done via a private `toResponse()` method in `AuditEventServiceImpl`; DTOs have no dependency on entities.
Flyway migrations live in `src/main/resources/db/migration`.

## Spec Rules

- Specs live in `specs/<feature>/`
- Specs must be written in English
- Acceptance criteria must use EARS-style phrasing
- Any list endpoint must define a deterministic sort order with a tie-breaker
- Agents must ask 5-7 clarifying questions before writing a spec
- The spec is the source of truth: if there is a gap, update the spec first and the code second

## AuditEvent Model

Required fields: `timestamp` (server-set only), `actor` (non-empty), `action`, `resource`, `outcome` (`success | denied | error`), `context` (free JSON).

## Domain Invariants — Never Violate

- **Append-only**: no UPDATE or DELETE — enforced at both application and DB levels
- `timestamp` is always set server-side; reject any client-supplied value
- `actor` is mandatory and must not be blank
- Invariant violations must throw domain-level errors, not be silently ignored
- Run existing tests and lint checks before finishing and make sure that they successfully passed; if not possible, clearly state what was not verified

## API

- `POST /audit-events` — ingest a single event
- `GET /audit-events` — search by `actor`, `resource`, or time range

## Testing Expectations

Integration tests via Testcontainers are required. They must cover:
- append-only enforcement (no update/delete paths exist)
- server-side timestamp assignment
- data persistence and retrieval
- search by actor, resource, and time range

ArchUnit tests (src/test/archunit) to check architectural approach

@RTK.md
