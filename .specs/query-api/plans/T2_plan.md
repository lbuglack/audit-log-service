# T2 Plan: Implement Query Validation, Normalization, and Error Responses

## Summary

Add the approved validation and normalization rules for the query endpoint, and return consistent machine-readable `400 Bad Request` responses for invalid query input.

## Implementation Changes

- Parse `from` and `to` from raw query-string values instead of relying only on Spring's `Instant` binding, so the query path can support both:
  - full UTC timestamps
  - date-only values
- Normalize `actor` and `resource` before the repository layer:
  - trim surrounding whitespace
  - treat empty strings as not provided
  - preserve the original logical value while preparing case-insensitive matching
- Normalize time bounds before query execution:
  - date-only `from` becomes `00:00:00Z` at the start of that UTC day
  - date-only `to` becomes the inclusive end of that UTC day
- Enforce approved validation rules:
  - reject malformed `from`
  - reject malformed `to`
  - reject non-UTC timestamp inputs
  - reject `from > to`
  - apply default `limit = 50`
  - reject `limit <= 0`
  - reject `limit > 50`
  - reject malformed cursor values
  - reject expired cursor values older than one hour
- Introduce a dedicated error response DTO with fields `code`, `message`, and `status`.
- Introduce a query-specific exception type plus controller-level exception mapping so every invalid query response uses the same `400` shape.

## Public Interfaces

- Invalid query input returns:

```json
{
  "code": "INVALID_CURSOR",
  "message": "The cursor is invalid or has expired.",
  "status": 400
}
```

- `code` must be machine-readable.
- `message` must explain the problem clearly enough for API consumers.
- `status` must match the HTTP status code.

## Test Plan

- Add controller/integration coverage for:
  - malformed `from`
  - malformed `to`
  - non-UTC timestamp input
  - date-only `from`
  - date-only `to`
  - `from > to`
  - blank `actor`
  - blank `resource`
  - `limit` omitted
  - `limit = 0`
  - `limit < 0`
  - `limit > 50`
  - malformed cursor
  - expired cursor
- Assert every invalid case returns `400` plus `code`, `message`, and `status`.

## Assumptions

- Validation failures must happen before any repository read so the API never returns partial results.
- Case-insensitive behavior is part of the approved contract and must be applied consistently for both actor and resource filters.
- Cursor-expiry validation is enforced by the query service using cursor metadata described in T3.
