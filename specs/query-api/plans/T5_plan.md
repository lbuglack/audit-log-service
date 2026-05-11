# T5 Plan: Backfill Regression Coverage and Consumer-Facing Documentation

## Summary

Complete the rollout with regression coverage for the approved query contract and update consumer documentation to describe the new query behavior accurately.

## Implementation Changes

- Expand automated coverage for the final `GET /audit-events` contract, including both happy-path and invalid-input scenarios.
- Keep append-only behavior protected by existing tests and ensure the new query work does not introduce update or delete paths.
- Update `README.md` so its query examples match the approved contract:
  - response envelope with `items` and optional `nextCursor`
  - case-insensitive actor/resource behavior
  - date-only `from` and `to`
  - `limit` default/max rules
  - structured `400` errors
- Document the externally visible contract change from a bare array response to the paginated envelope.

## Test Plan

- Keep existing create-event and append-only regression tests green.
- Extend or split query integration coverage so the final suite explicitly covers:
  - no filters
  - actor-only
  - resource-only
  - actor + resource
  - case-insensitive matches
  - empty-string filters ignored
  - `from` only
  - `to` only
  - bounded range
  - date-only `from`
  - date-only `to`
  - empty result set
  - default `limit`
  - smaller `limit`
  - `limit = 50`
  - stable timestamp-desc ordering
  - `id DESC` tie-break ordering
  - multi-page cursor traversal
  - last page without `nextCursor`
  - expired cursor
  - malformed cursor
  - malformed timestamp
  - non-UTC timestamp
  - `from > to`
  - invalid `limit`
  - structured `400` error body
- Run the full Gradle test suite as the final verification step.

## Documentation Deliverables

- Update the `GET /audit-events` section in `README.md` to show:
  - request parameters
  - paginated success response
  - follow-up request using `cursor`
  - validation expectations for `400` responses
- Update the README index section to reflect the new case-insensitive functional indexes if the migration replaces the old search indexes.

## Assumptions

- Query test coverage can remain in `AuditEventControllerIT` if it stays manageable, but a dedicated query-focused integration test class is acceptable if the current file becomes too large.
- Documentation updates are limited to repository docs and examples; no external changelog system is assumed.
