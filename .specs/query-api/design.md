# Design

## API contract

```http
GET /audit-events
  ?actor=u_42
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
- `context` may be `null`.
- `nextCursor` is included only when more matching results remain after the current page.
- The endpoint is read-only.
- Invalid `from` returns `400 Bad Request`.
- Invalid `to` returns `400 Bad Request`.
- Invalid `cursor` returns `400 Bad Request`.
- Invalid `limit` returns `400 Bad Request`.
- `from > to` returns `400 Bad Request`.
- Field naming should stay aligned to the existing project model: `timestamp`, `actor`, `action`, `resource`, `outcome`, and `context`.

Error response shape for invalid query input:

```json
{
  "code": "INVALID_CURSOR",
  "message": "The cursor is invalid or has expired.",
  "status": 400
}
```

- All `400 Bad Request` responses for invalid query parameters must include `code`, `message`, and `status`.
- `code` is machine-readable.
- `message` is customer-readable and explains what is wrong with the request.
- `status` is the numeric HTTP status code.

## Sort & determinism

- Results are sorted by `timestamp DESC`.
- Requests without filters are allowed and return all records in this order.
- Pagination must avoid loss or duplication across pages.
- Events with the same `timestamp` are secondarily sorted by `id DESC`.
- The effective deterministic order is `timestamp DESC, id DESC`.
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
- A cursor older than one hour must be rejected with `400 Bad Request`.
- Offset pagination should not be used because it is more vulnerable to drift, duplication, and missing records when new events are appended during traversal.

## Indexes

Proposed index strategy optimized for cursor pagination on `timestamp`:

- `idx_audit_events_timestamp_id_desc (timestamp DESC, id DESC)`
- `idx_audit_events_lower_actor_timestamp_id_desc (lower(actor), timestamp DESC, id DESC)`
- `idx_audit_events_lower_resource_timestamp_id_desc (lower(resource), timestamp DESC, id DESC)`
- `idx_audit_events_lower_actor_lower_resource_timestamp_id_desc (lower(actor), lower(resource), timestamp DESC, id DESC)`

Reasoning:

- The base index supports global reverse-chronological scans and stable cursor pagination.
- The actor-specific index supports case-insensitive exact-match actor filtering plus ordered pagination.
- The resource-specific index supports case-insensitive exact-match resource filtering plus ordered pagination.
- The combined actor/resource index supports the case where both case-insensitive exact-match filters are present.
- Including `id` in each ordered index supports deterministic tie-breaking for events that share the same `timestamp`.

## Validation rules

- `actor` is a case-insensitive exact-match filter when provided.
- `resource` is a case-insensitive exact-match filter when provided.
- Empty-string values for `actor` and `resource` are treated as not provided.
- `from` and `to` are inclusive UTC bounds when provided.
- Date-only `from` values are interpreted as the start of the day in UTC.
- Date-only `to` values are interpreted as the inclusive end of the day in UTC.
- Open-ended queries are allowed with only `from` or only `to`.
- Requests with no filters are allowed.
- When `actor` and `resource` are both provided, they are combined with logical `AND`.
- Invalid query parameters must not return partial results.
- `limit` must be greater than zero and less than or equal to `50`.
- `cursor` must be rejected with `400 Bad Request` when it is malformed, expired, or does not match the expected cursor format.
- Non-UTC timestamp inputs must be rejected with `400 Bad Request`.
- Invalid query parameter responses must use the error body shape `{ "code": "...", "message": "...", "status": 400 }`.

## Integration with api / domain / infrastructure layers

- `controller`
  - Exposes `GET /audit-events`.
  - Accepts query parameters `actor`, `resource`, `from`, `to`, `cursor`, and `limit`.
  - Returns a response object with `items` and optional `nextCursor`.
  - Performs HTTP-level validation and returns machine-readable `400 Bad Request` responses for invalid parameters.
- `facade`
  - Coordinates the query use case and keeps orchestration logic out of the controller.
  - Delegates to the service layer and maps the service result to the API response contract if needed.
- `service`
  - Owns query rules such as case-insensitive filtering, empty-string normalization, UTC date-bound normalization, default/max limit handling, cursor decoding, cursor expiry handling, deterministic ordering, and next-cursor generation.
  - Executes the read-only transaction boundary.
- `dao/repository`
  - Performs filtered and ordered reads from the append-only audit store.
  - Applies database predicates for actor, resource, and time range.
  - Applies case-insensitive matching for actor and resource filters.
  - Uses the pagination sort order `timestamp DESC, id DESC`.
- `dto`
  - Defines the paginated read-model returned by the query endpoint.
  - Defines the error response model for invalid query input.
  - Keeps the external API shape separate from the persistence entity.

Mapping note:

- The current repository structure should be preserved: `controller -> facade -> service -> dao/repository`, with DTOs used at the API boundary and no entity exposure from the controller layer.

## AGENTS.md alignment

- Must preserve append-only behavior.
- Must keep the endpoint read-only.
- Must fit the repository layering: controller, facade, service, dao, dto.
- Must keep invariant enforcement explicit rather than silently ignoring invalid input.
- Must maintain server-authoritative audit data and avoid introducing any update or delete path.
