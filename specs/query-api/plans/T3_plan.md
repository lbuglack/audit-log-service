# T3 Plan: Refactor Query Execution for Deterministic Cursor Pagination

## Summary

Implement stable seek-based pagination for `GET /audit-events` using the approved sort order `timestamp DESC, id DESC`, one-hour cursor validity, and response-side `nextCursor` generation.

## Implementation Changes

- Replace unbounded query execution for the read path with seek pagination.
- Use the effective ordering:
  - primary sort: `timestamp DESC`
  - secondary sort: `id DESC`
- Query `limit + 1` rows so the service can determine whether more results remain after the current page.
- Build `nextCursor` only when an extra matching row exists after the current page.
- Omit `nextCursor` when the returned page fully exhausts the result set.
- Define the cursor payload as an opaque Base64URL-encoded JSON document containing:
  - schema version
  - issued-at timestamp
  - last returned `timestamp`
  - last returned `id`
  - normalized filter fingerprint for `actor`, `resource`, `from`, and `to`
  - effective `limit`
- Reject cursors that:
  - cannot be decoded
  - are missing required fields
  - are older than one hour
  - do not match the normalized filters or effective limit of the current request
- Use the standard seek predicate for the next page:
  - `timestamp < lastTimestamp`
  - or `timestamp = lastTimestamp AND id < lastId`
- Preserve read-only behavior and keep entity-to-DTO mapping inside the service layer.

## Public Interfaces

- The cursor remains opaque to clients.
- `nextCursor` is generated from the last returned item of a non-final page.
- A fresh first-page request may see newly appended rows, but an in-progress paginated traversal must not skip or duplicate rows.

## Test Plan

- Add integration coverage for:
  - first page with `nextCursor`
  - final page without `nextCursor`
  - multi-page traversal with no skipped rows
  - multi-page traversal with no duplicated rows
  - duplicate timestamps resolved by `id DESC`
  - cursor reuse with matching filters
  - cursor rejection when filters differ from the original request
  - cursor rejection when limit differs from the original request
  - cursor rejection after one hour
- Include at least one scenario where new rows are inserted between separate first-page requests, proving fresh queries can still see appended data.

## Assumptions

- Cursor opacity does not require cryptographic signing for this version; Base64URL-encoded structured cursor data is sufficient if strict decoding and request-context matching are enforced.
- Pagination stability takes precedence over surfacing newly appended earlier rows in later pages of an already-started traversal.
