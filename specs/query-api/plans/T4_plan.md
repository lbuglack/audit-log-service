# T4 Plan: Add Repository and Database Support for Case-Insensitive Filtered Seek Pagination

## Summary

Implement the repository read path and database indexes needed for the approved case-insensitive filtered query behavior and deterministic seek pagination.

## Implementation Changes

- Extend the repository layer with a dedicated paginated query method instead of continuing to rely on `JpaSpecificationExecutor.findAll(spec)`.
- Implement the query in a custom repository component so one read path can handle all approved combinations:
  - no filters
  - actor-only
  - resource-only
  - actor + resource
  - open-ended time ranges
  - bounded time ranges
  - seek-pagination continuation
- Apply query predicates as follows:
  - case-insensitive exact match for `actor` via `lower(actor) = :actor`
  - case-insensitive exact match for `resource` via `lower(resource) = :resource`
  - inclusive `timestamp >= :from`
  - inclusive `timestamp <= :to`
  - seek predicate using last page `timestamp` and `id`
- Order every query explicitly by `timestamp DESC, id DESC`.
- Add the next Flyway migration after `V3__immutability_triggers.sql` to replace the current search-index strategy with the approved pagination-oriented indexes:
  - drop the older search indexes from `V2__add_search_indexes.sql` that no longer fit the approved query pattern
  - create `idx_audit_events_timestamp_id_desc (timestamp DESC, id DESC)`
  - create `idx_audit_events_lower_actor_timestamp_id_desc (lower(actor), timestamp DESC, id DESC)`
  - create `idx_audit_events_lower_resource_timestamp_id_desc (lower(resource), timestamp DESC, id DESC)`
  - create `idx_audit_events_lower_actor_lower_resource_timestamp_id_desc (lower(actor), lower(resource), timestamp DESC, id DESC)`
- Leave append-only triggers and entity immutability settings untouched.

## Public Interfaces

- No external HTTP contract changes are introduced in T4.
- Repository behavior must match the approved query semantics already exposed through the boundary in T1-T3.

## Test Plan

- Add Testcontainers-backed integration coverage for:
  - case-insensitive actor filtering
  - case-insensitive resource filtering
  - combined actor/resource filtering
  - inclusive time bounds
  - open-ended time bounds
  - no-filter descending scans
  - paginated traversal using seek predicates
- Ensure ordering assertions cover equal timestamps and `id DESC` tie-breaking.

## Assumptions

- A custom repository implementation is preferred over increasingly complex `Specification`-only logic because the approved behavior now requires explicit seek predicates and stable ordering.
- Replacing the older search indexes is acceptable because the new functional and descending indexes are the approved query strategy.
