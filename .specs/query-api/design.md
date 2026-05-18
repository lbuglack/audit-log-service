# Design

## API contract

```http
GET /audit-events
  ?actor=u_42,svc_orders
  &resource=order/9f3b...
  &from=2026-04-01T00:00:00Z
  &to=2026-05-01T00:00:00Z
  &cursor=...
  &limit=50
```

Success response:

- `200 OK`
- Top-level response shape is a JSON object with `items` and optional `nextCursor`.

Success response shape:

```json
{
  "items": [
    {
      "id": "01HE...Z9",
      "timestamp": "2026-04-17T11:02:14Z",
      "actor": "u_42",
      "action": "order.refunded",
      "resource": "order/9f3b...",
      "outcome": "success",
      "context": null
    }
  ],
  "nextCursor": "opaque-cursor"
}
```

- Returned events are stored audit records in descending timestamp order.
- `actor` is an optional comma-separated query parameter that accepts up to 10 supplied values per request.
- Leading and trailing whitespace around each supplied actor value is ignored before matching.
- Actor values that become empty after trimming are ignored, and the actor filter is treated as not provided when no non-empty actor values remain.
- Repeated actor values still count toward the 10-value maximum, but duplicate normalized actor values are deduplicated before query execution.
- Actor matching is case-insensitive exact-match against an unordered normalized actor set.
- `context` may be `null`.
- `nextCursor` is included only when more matching results remain after the current page.
- The endpoint is read-only.
- Invalid `from` returns `400 Bad Request`.
- Invalid `to` returns `400 Bad Request`.
- Invalid `cursor` returns `400 Bad Request`.
- Invalid `limit` returns `400 Bad Request`.
- More than 10 supplied actor values returns `422 Unprocessable Entity`.
- `from > to` returns `400 Bad Request`.
- Field naming should stay aligned to the existing project model: `timestamp`, `actor`, `action`, `resource`, `outcome`, and `context`.

Error response shape for invalid query input (`400` or `422`):

```json
{
  "code": "TOO_MANY_ACTOR_VALUES",
  "message": "actor accepts at most 10 values.",
  "status": 422
}
```

- All `400 Bad Request` and `422 Unprocessable Entity` responses for invalid query parameters must include `code`, `message`, and `status`.
- `code` is machine-readable.
- `message` is customer-readable and explains what is wrong with the request.
- `status` is the numeric HTTP status code for the response that was returned.

## Sort & determinism

- Results are sorted by `timestamp DESC`.
- Requests without filters are allowed and return all records in this order.
- Pagination must avoid loss or duplication across pages.
- Events with the same `timestamp` are secondarily sorted by `id DESC`.
- The effective deterministic order is `timestamp DESC, id DESC`.
- Actor-list order does not influence matching, cursor identity, or result order.
- When a client starts a paginated traversal, stability wins over showing newly appended earlier rows in later pages of that same traversal.
- Newly appended events remain visible to fresh queries started from the first page.

## Pagination strategy

- Cursor-based pagination is required for large result sets.
- The API supports `cursor`.
- The API supports `limit`.
- `limit` is optional.
- Default `limit` is `50`.
- Maximum `limit` is `50`.
- Clients may request a smaller page size than `50`.
- The response must include `nextCursor` when more matching results remain after the current page.
- The response must omit `nextCursor` when there are no further results.
- The cursor must be opaque to clients.
- The cursor must encode the last returned sort position so the next page can continue after `timestamp DESC, id DESC`.
- The cursor must carry enough information to enforce a one-hour validity window from the time it was issued.
- The cursor must bind to the normalized filter set from the first page of the traversal.
- The normalized filter set treats actor values as a deduplicated, case-normalized, unordered set after trimming and empty-value removal, and includes the normalized `resource`, `from`, and `to` filters.
- If a later page request presents a cursor with a different normalized filter set than the one the cursor was issued for, the API must reject the request with `400 Bad Request`.
- A cursor older than one hour must be rejected with `400 Bad Request`.
- Offset pagination should not be used because it is more vulnerable to drift, duplication, and missing records when new events are appended during traversal.

## Indexes

Proposed index strategy optimized for multi-actor cursor pagination on `timestamp`:

- `idx_audit_events_timestamp_id_desc (timestamp DESC, id DESC)`
- `idx_audit_events_lower_actor_timestamp_id_desc (lower(actor), timestamp DESC, id DESC)`
- `idx_audit_events_lower_resource_timestamp_id_desc (lower(resource), timestamp DESC, id DESC)`
- `idx_audit_events_lower_actor_lower_resource_timestamp_id_desc (lower(actor), lower(resource), timestamp DESC, id DESC)`

Reasoning:

- The base index supports global reverse-chronological scans and stable cursor pagination.
- The actor-specific index is the primary multi-actor-friendly actor index and supports case-insensitive exact-match filtering for one or more actor values plus ordered pagination.
- The actor-specific index must support lookups against the normalized actor set without requiring a full table scan.
- The resource-specific index supports case-insensitive exact-match resource filtering plus ordered pagination.
- The combined actor/resource index supports the case where both case-insensitive exact-match filters are present.
- Including `id` in each ordered index supports deterministic tie-breaking for events that share the same `timestamp`.

## Validation rules

- `actor` is parsed as a comma-separated list when provided.
- Leading and trailing whitespace is trimmed from each supplied actor value before validation and matching.
- Actor values that become empty after trimming are ignored.
- The actor filter is treated as not provided when no non-empty actor values remain after trimming.
- Repeated actor values count toward the maximum of 10 supplied actor values.
- After validation, actor values are deduplicated, case-normalized, and treated as an unordered set for matching and cursor binding.
- Each normalized actor value participates in case-insensitive exact-match filtering, and multiple actor values are combined with logical `OR`.
- Requests with more than 10 supplied actor values must return `422 Unprocessable Entity`.
- `resource` is a case-insensitive exact-match filter when provided.
- Empty-string values for `resource` are treated as not provided.
- `from` and `to` are inclusive UTC bounds when provided.
- Date-only `from` values are interpreted as the start of the day in UTC.
- Date-only `to` values are interpreted as the inclusive end of the day in UTC.
- Open-ended queries are allowed with only `from` or only `to`.
- Requests with no filters are allowed.
- When `actor`, `resource`, `from`, and `to` are combined, an event must match at least one normalized actor value and every other supplied filter.
- Invalid query parameters must not return partial results.
- `limit` must be greater than zero and less than or equal to `50`.
- `cursor` must be rejected with `400 Bad Request` when it is malformed, expired, or does not match the normalized filter set it was issued for.
- Non-UTC timestamp inputs must be rejected with `400 Bad Request`.
- Invalid query parameter responses must use the error body shape `{ "code": "...", "message": "...", "status": <400-or-422> }`.

## Integration with api / domain / infrastructure layers

- `controller`
  - Exposes `GET /audit-events`.
  - Accepts query parameters `actor`, `resource`, `from`, `to`, `cursor`, and `limit`.
  - Parses `actor` as a comma-separated query parameter and returns machine-readable `400` or `422` responses for invalid input.
  - Returns a response object with `items` and optional `nextCursor`.
  - Performs HTTP-level validation and returns machine-readable `400 Bad Request` or `422 Unprocessable Entity` responses for invalid parameters.
- `facade`
  - Coordinates the query use case and keeps orchestration logic out of the controller.
  - Delegates to the service layer and maps the service result to the API response contract if needed.
- `service`
  - Owns query rules such as actor-list trimming, empty-value removal, max-count validation, deduplication, case-insensitive filtering, UTC date-bound normalization, default/max limit handling, cursor decoding, cursor expiry handling, normalized filter-set binding, deterministic ordering, and next-cursor generation.
  - Executes the read-only transaction boundary.
- `dao/repository`
  - Performs filtered and ordered reads from the append-only audit store.
  - Applies database predicates for the normalized actor set, resource, and time range.
  - Applies case-insensitive matching for actor membership and resource filters.
  - Uses the pagination sort order `timestamp DESC, id DESC`.
- `dto`
  - Defines the paginated read-model returned by the query endpoint.
  - Defines the error response model for invalid query input across both `400` and `422` responses.
  - Keeps the external API shape separate from the persistence entity.

Mapping note:

- The current repository structure should be preserved: `controller -> facade -> service -> dao/repository`, with DTOs used at the API boundary and no entity exposure from the controller layer.

## AGENTS.md alignment

- Must preserve append-only behavior.
- Must keep the endpoint read-only.
- Must fit the repository layering: controller, facade, service, dao, dto.
- Must keep invariant enforcement explicit rather than silently ignoring invalid input.
- Must maintain server-authoritative audit data and avoid introducing any update or delete path.
