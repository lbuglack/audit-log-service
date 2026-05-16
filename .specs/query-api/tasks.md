# Tasks

## T1. Refactor the query API boundary to the approved contract

Refs — `requirements.md`: all query acceptance criteria; `design.md`: `API contract`, `Validation rules`, `Integration with api / domain / infrastructure layers`

Dependencies — None

DoD —
- `GET /audit-events` accepts the approved query parameters for `actor`, `resource`, `from`, `to`, `cursor`, and `limit`.
- The query endpoint returns the approved response envelope with `items` and optional `nextCursor` instead of the current bare `List<AuditEventResponse>` shape.
- The query path uses dedicated request/response DTOs where needed so the search contract can evolve independently from the create-event response model.
- The facade and service interfaces are updated to accept a query model rather than the current four-argument search signature.
- The controller boundary stays read-only and remains aligned with the existing repository layering.

Size — 1 safe commit / PR

## T2. Implement query validation, normalization, and error responses

Refs — `requirements.md`: `Compliance officer`, `Security analyst`, `Validation and error handling`; `design.md`: `API contract`, `Validation rules`, `Integration with api / domain / infrastructure layers`

Dependencies — `T1`

DoD —
- `actor` and `resource` filtering is implemented as exact case-insensitive matching.
- Empty-string values for `actor` and `resource` are normalized to “not provided” instead of acting as real filters.
- `from` and `to` accept approved UTC inputs, including date-only normalization to start-of-day and inclusive end-of-day behavior.
- The query path enforces `limit` default `50`, maximum `50`, and rejects invalid values with `400 Bad Request`.
- Invalid query input returns the approved machine-readable error body with `code`, `message`, and `status`.
- Controller or HTTP-level integration tests cover malformed timestamps, non-UTC timestamps, invalid limits, malformed cursors, expired cursors, and `from > to`.

Size — 1 safe commit / PR

## T3. Refactor query execution for deterministic cursor pagination

Refs — `requirements.md`: `Security analyst`, `Validation and error handling`; `design.md`: `Sort & determinism`, `Pagination strategy`, `Integration with api / domain / infrastructure layers`, `AGENTS.md alignment`

Dependencies — `T1`, `T2`

DoD —
- Query execution uses the approved deterministic order `timestamp DESC, id DESC`.
- The service layer preserves read-only behavior while enforcing page-size rules and query normalization outcomes from `T2`.
- Cursor encoding, decoding, one-hour expiry handling, and next-page progression are implemented according to the approved contract.
- `nextCursor` is returned only when more matching results remain and is omitted on the last page.
- Pagination behavior avoids loss or duplication across pages, even when multiple rows share the same `timestamp`.
- Fresh queries from the first page can see newly appended rows without weakening the stability of an in-progress paginated traversal.

Size — 1 safe commit / PR

## T4. Add repository and database support for case-insensitive filtered seek pagination

Refs — `requirements.md`: `Compliance officer`, `SRE`, `Security analyst`; `design.md`: `Indexes`, `Sort & determinism`, `Pagination strategy`, `Validation rules`, `Integration with api / domain / infrastructure layers`

Dependencies — `T1`, `T2`, `T3`

DoD —
- Repository querying supports case-insensitive actor and resource filters, inclusive time bounds, open-ended time ranges, no-filter queries, and cursor predicates in one read path.
- The repository implementation is explicit about ordering and seek-pagination predicates rather than relying on unbounded `findAll(spec)` behavior or incidental database row order.
- A Flyway migration adds the approved descending and functional indexes for `timestamp`, `lower(actor)`, and `lower(resource)` query patterns.
- Query performance work stays within the existing controller -> facade -> service -> dao/repository layering and does not introduce any update or delete behavior.
- Testcontainers-backed integration coverage exercises combined filters together with seek pagination.

Size — 1 safe commit / PR

## T5. Backfill regression coverage and consumer-facing documentation

Refs — `requirements.md`: all sections; `design.md`: `API contract`, `Sort & determinism`, `Validation rules`, `AGENTS.md alignment`

Dependencies — `T1`, `T2`, `T3`, `T4`

DoD —
- Integration tests cover no filters, actor-only, resource-only, case-insensitive matching, combined actor and resource, inclusive `from`/`to`, open-ended time queries, date-only inputs, empty-string filters being ignored, empty results, default limit, smaller limit, max limit, stable ordering, next-cursor progression, cursor expiry, and invalid input returning structured `400` errors.
- Existing append-only behavior remains covered, and no update/delete path is introduced by the query changes.
- `README.md` and any query examples are updated to match the approved query contract, especially the paginated response envelope and validation behavior.
- Consumer-facing rollout notes document the externally visible contract change from the current bare-array search response to the approved `items` plus optional `nextCursor` model.
- `./gradlew test` passes with the final implementation.

Size — 1 safe commit / PR
