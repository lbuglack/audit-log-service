# Audit Log Service

An internal append-only service for ingesting and querying audit events. Designed for compliance, security monitoring, and operational observability.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 21 |
| Framework | Spring Boot 3.3 |
| Build | Gradle 8.6 (Kotlin DSL) |
| Database | PostgreSQL 16 |
| Migrations | Flyway |
| Testing | JUnit 5, Testcontainers, ArchUnit |

## Prerequisites

- Java 21
- Docker (for local Postgres and integration tests)

## Getting Started

**1. Start the database**
```bash
docker compose up -d
```

**2. Run the application**
```bash
./gradlew bootRun
```

The service starts on `http://localhost:8081`.

**3. Run tests**
```bash
./gradlew test
```

Testcontainers spins up an isolated Postgres instance automatically — no manual setup needed for tests.

## API

### POST /audit-events
Ingest a single audit event. `timestamp` is always assigned by the server.

```bash
curl -X POST http://localhost:8081/audit-events \
  -H "Content-Type: application/json" \
  -d '{
    "actor":    "user:42",
    "action":   "resource.updated",
    "resource": "project:1",
    "outcome":  "success",
    "context":  { "ip": "10.0.0.1" }
  }'
```

**Request fields**

| Field | Type | Required | Notes |
|---|---|---|---|
| `actor` | string | yes | User ID or service account; must not be blank |
| `action` | string | yes | e.g. `user.login`, `resource.updated` |
| `resource` | string | yes | Target object, e.g. `project:42` |
| `outcome` | string | yes | `success`, `denied`, or `error` |
| `context` | object | no | Arbitrary JSON metadata |

**Response — 201 Created**
```json
{
  "id":        "af79ba8e-d767-420d-a2f3-55d1d7097eeb",
  "timestamp": "2026-04-27T14:26:25.323249Z",
  "actor":     "user:42",
  "action":    "resource.updated",
  "resource":  "project:1",
  "outcome":   "success",
  "context":   { "ip": "10.0.0.1" }
}
```

---

### GET /audit-events
Search events. All parameters are optional and combinable. Results are ordered by `timestamp` descending.

| Parameter | Type | Example |
|---|---|---|
| `actor` | string | `?actor=user:42` |
| `resource` | string | `?resource=project:1` |
| `from` | ISO 8601 | `?from=2026-04-01T00:00:00Z` |
| `to` | ISO 8601 | `?to=2026-04-30T23:59:59Z` |

```bash
# by actor
curl "http://localhost:8081/audit-events?actor=user:42"

# by resource + time range
curl "http://localhost:8081/audit-events?resource=project:1&from=2026-04-01T00:00:00Z&to=2026-04-30T23:59:59Z"
```

## Architecture

The service uses a strict layered architecture enforced at build time by ArchUnit:

```
controller  →  facade  →  service  →  dao
                              ↕
                             dto
```

| Package | Responsibility |
|---|---|
| `controller` | HTTP entry points, input validation (`@Valid`) |
| `facade` | Orchestration between controller and service |
| `service` | Business logic, transactions, entity→DTO mapping |
| `dao/entity` | JPA entities — all columns `updatable = false` |
| `dao/repository` | Spring Data JPA + `JpaSpecificationExecutor` for dynamic search |
| `dto/request` | Incoming request records with Bean Validation |
| `dto/response` | Outgoing response records |

### Domain invariants

- **Append-only** — no `UPDATE` or `DELETE` endpoints exist; `updatable = false` enforces this at the ORM level
- **Server-side timestamp** — `timestamp` is set in the service layer; any client-supplied value is ignored
- **Mandatory actor** — requests with a blank or missing `actor` are rejected with `400`

### Database indexes

Five indexes cover the common query patterns:

| Index | Query pattern |
|---|---|
| `idx_audit_events_actor` | `?actor=` |
| `idx_audit_events_resource` | `?resource=` |
| `idx_audit_events_timestamp_desc` | `?from=&to=` and default sort |
| `idx_audit_events_actor_timestamp` | `?actor=` + time range |
| `idx_audit_events_resource_timestamp` | `?resource=` + time range |

## Database connection (local)

```
Host:     localhost:5432
Database: audit_log
User:     audit_user
Password: audit_pass
```

Managed by `docker-compose.yml`. Data is persisted in a named Docker volume (`postgres_data`).

## Configuration profiles

| Profile | How to activate | Purpose |
|---|---|---|
| default | — | Local dev, connects to docker-compose Postgres |
| `local` | `--spring.profiles.active=local` | Same as default + SQL query logging |
| `test` | Applied automatically by `@ActiveProfiles("test")` | Testcontainers, datasource configured dynamically |
