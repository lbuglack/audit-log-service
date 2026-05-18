# Multi-Actor Filter Plan

## Summary

Extend `GET /audit-events` to support comma-separated multi-actor filtering while preserving read-only behavior, deterministic pagination, and the existing `controller -> facade -> service -> dao/repository` layering. This plan covers the follow-up work captured in `T6`, `T7`, and `T8`: actor-list parsing and validation, `400 Bad Request` for empty actor values, `422 Unprocessable Entity` validation for oversized actor lists, cursor binding to the normalized filter set, repository and index updates for multi-actor retrieval, and regression/documentation backfill.

## Task Alignment

- `T6` covers query parsing, validation, and cursor semantics for multi-actor filters.
- `T7` covers repository and database/index changes for multi-actor seek pagination.
- `T8` covers regression coverage and consumer-facing contract updates for the multi-actor delta.

## Implementation Changes

- Extend the query boundary so `actor` is parsed as a comma-separated query parameter instead of a single logical value.
- Normalize actor input before repository execution:
  - trim leading and trailing whitespace around each supplied actor value
  - reject any supplied actor value that becomes empty after trimming with `400 Bad Request`
  - count supplied comma-separated actor values before deduplication
  - allow happy-path requests with exactly `1`, `3`, or `10` supplied actor values when each supplied value remains non-empty after trimming
  - deduplicate normalized actor values after validation so matching and cursor identity operate on an unordered normalized actor set
- Preserve the approved filter semantics:
  - actor values combine with logical `OR`
  - `resource`, `from`, and `to` continue to combine with logical `AND`
  - result ordering remains `timestamp DESC, id DESC`
- Extend invalid-input handling so requests with an empty actor value return the structured `400 Bad Request` body with `code`, `message`, and `status`, and requests with exactly `11` supplied actor values return the structured `422 Unprocessable Entity` body with `code`, `message`, and `status`.
- Preserve existing `400 Bad Request` behavior for malformed timestamps, non-UTC timestamps, invalid limits, malformed cursors, expired cursors, and cursor/filter mismatches.
- Update cursor generation and validation so each cursor is bound to the normalized filter set from the first page:
  - actor-list order must not affect cursor identity
  - duplicate actor values must not change the normalized actor set used for cursor matching
  - later requests using a cursor with a different normalized actor/resource/time filter set must be rejected
- Extend the repository read path for case-insensitive actor membership filtering while keeping explicit seek predicates and explicit ordering, including the full mixed filter set of `actor`, `resource`, `from`, and `to`.
- Add or adjust the actor-oriented pagination index so multi-actor case-insensitive retrieval can use the approved sort order without a full table scan.
- Update README examples and rollout notes to document:
  - comma-separated actor filtering
  - unordered actor semantics
  - the empty-actor `400` validation path
  - the `422` validation path for `11` supplied actor values
  - the existing paginated `items` plus optional `nextCursor` response model

## Public Interfaces

- `GET /audit-events` accepts `actor` as an optional comma-separated query parameter with between `1` and `10` supplied non-empty values when present, for example:

```http
GET /audit-events?actor=u_42,svc_orders,svc_payments&resource=order/9f3b&limit=50
```

- Multi-actor requests return the same paginated success envelope already approved for the query API:

```json
{
  "items": [
    {
      "id": "01HE...Z9",
      "timestamp": "2026-04-17T11:02:14Z",
      "actor": "u_42",
      "action": "order.refunded",
      "resource": "order/9f3b",
      "outcome": "success",
      "context": null
    }
  ],
  "nextCursor": "opaque-cursor"
}
```

- Requests with an empty supplied actor value return `400 Bad Request` using the standard structured error body.
- Requests with exactly `11` supplied actor values return:

```json
{
  "code": "TOO_MANY_ACTOR_VALUES",
  "message": "actor accepts at most 10 values.",
  "status": 422
}
```

- `nextCursor` remains opaque to API consumers, but internally it must remain tied to the normalized actor/resource/time filter set and effective page size of the original traversal.

## Test Plan

- Add controller or integration coverage for:
  - happy-path multi-actor actor-only queries with exactly `1`, `3`, and `10` supplied actor values
  - multi-actor queries combined with `resource`
  - multi-actor queries combined with `from` and `to`
  - whitespace trimming around non-empty actor values
  - empty supplied actor values, including values that become empty after trimming, returning structured `400`
  - exactly `11` supplied actor values returning structured `422`
- Add pagination coverage for:
  - stable ordering with multi-actor result sets
  - shared-timestamp rows resolved by `id DESC`
  - `nextCursor` progression across multi-actor pages
  - cursor reuse with logically equivalent actor lists after normalization
  - cursor rejection when the normalized actor set differs
  - keyset pagination when `actor`, `resource`, `from`, and `to` are all supplied together
  - cursor rejection when `resource`, `from`, `to`, or `limit` differs from the original traversal
- Add Testcontainers-backed repository coverage for:
  - case-insensitive membership matching against multiple actor values
  - multi-actor queries combined with the full mixed filter set of `resource`, `from`, and `to`
  - seek-pagination behavior with multi-actor filters
  - actor-oriented index-backed query behavior remaining aligned with append-only constraints
- Run `./gradlew test` as the final verification step once the implementation is complete.

## Documentation Deliverables

- Update the `GET /audit-events` documentation in `README.md` with a multi-actor example request.
- Document the normalization rules for actor values, especially trimming, empty-value rejection, deduplication, supplied-value counting before deduplication, and unordered semantics.
- Document the new empty-actor `400 Bad Request` validation case alongside the `422 Unprocessable Entity` validation case for `11` supplied actor values and the existing structured `400` errors.
- Update rollout notes to call out the externally visible expansion from single-actor filtering to multi-actor filtering.

## Assumptions

- The existing query boundary, cursor model, and custom repository direction from `T1`-`T5` remain the baseline and are extended rather than replaced.
- Actor deduplication is an execution detail applied after validation; duplicates still count toward the maximum number of supplied comma-separated values.
- No write-side behavior changes are introduced, and append-only invariants remain untouched throughout the multi-actor work.
