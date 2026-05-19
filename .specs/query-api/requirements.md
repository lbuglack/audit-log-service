# Problem

The system needs a read-only HTTP endpoint that allows internal users to retrieve audit events by one or more actors, resource, and time range without changing stored data.

The endpoint must let internal users:

- retrieve audit events for one or more actors by passing `actor` as a comma-separated query parameter
- retrieve audit events for a specific resource
- retrieve audit events within a time range
- combine filters in a single request
- page through large result sets safely
- reject requests that provide an empty actor value
- reject requests that provide more than 10 actor values in one request
- query audit data without mutating stored events

# User stories with AC

## Compliance officer

As a Compliance Officer, I want to retrieve audit events for one or more actors, resource, and time range so that I can confirm or refute whether a specific action happened during an audit involving multiple identities.

Acceptance criteria:

1. When `actor` is provided as a comma-separated list, the API shall return only events whose actor exactly matches at least one supplied actor value using case-insensitive comparison.
2. When `actor` is provided with whitespace around non-empty values, the API shall ignore leading and trailing whitespace around each supplied actor value before matching.
3. When `actor` contains between 1 and 10 supplied comma-separated values and each supplied value remains non-empty after trimming, the API shall accept the actor filter and apply logical `OR` across the supplied actor values.
4. When `actor`, `resource`, `from`, and/or `to` are provided together, the API shall return an event only if it matches at least one supplied actor value and satisfies every other supplied filter.
5. When `resource` is provided, the API shall return only events with the exact case-insensitive matching resource.
6. If `resource` is provided as an empty string, the API shall ignore that filter.
7. When no filters are provided, the API shall return all records sorted by `timestamp` descending with `id` descending as the tie-breaker.
8. When `from` and `to` are provided, the API shall return only events with `timestamp >= from` and `timestamp <= to`.
9. When only `from` is provided, the API shall return only events with `timestamp >= from`.
10. When only `to` is provided, the API shall return only events with `timestamp <= to`.
11. When a request succeeds with matching events, the API shall return a response containing an `items` array, and each returned event shall contain `id`, `timestamp`, `actor`, `action`, `resource`, `outcome`, and `context`.
12. When a returned event has no context data, the API shall allow `context` to be `null`.
13. When no events match the filters, the API shall return an empty `items` array.

## SRE

As an SRE, I want to reconstruct the timeline of actions on a resource during an incident so that I can understand what happened and in what order.

Acceptance criteria:

1. When multiple matching events exist for the same resource, the API shall return the events sorted by `timestamp` descending, with `id` descending as the tie-breaker.
2. When `resource`, `from`, and `to` are supplied for a time-bounded incident investigation, the API shall return only events for that resource in the inclusive time window.
3. While the endpoint serves read-only audit queries, the API shall not create, update, or delete audit records.
4. When an event is returned for timeline reconstruction, the API shall reflect stored audit data and shall not omit the actor, resource, action, or timestamp information needed by the SRE.

## Security analyst

As a Security Analyst, I want to paginate through a large multi-actor result set without loss or duplication so that I can investigate related identities across high-volume audit history reliably.

Acceptance criteria:

1. When more matching records exist than fit in one page and the client requests `limit=50` while filtering by one or more actor values, the API shall return no more than 50 events in that page.
2. When the client omits `limit`, the API shall use the default page size of `50`.
3. When successive page requests use `cursor` together with `actor`, `resource`, `from`, and `to` for the same mixed filter set, the API shall not skip or duplicate records between pages.
4. When more matching records remain after the current page, the response shall include `nextCursor`.
5. When no further matching records remain after the current page, the response shall omit `nextCursor`.
6. When multiple matching events share the same `timestamp` in a paginated multi-actor query, the API shall return the events sorted by `timestamp` descending with `id` descending as the tie-breaker.
7. When `actor` contains repeated values, the API shall count each supplied value toward the maximum number of actor values allowed in one request.
8. If `actor` contains 11 comma-separated values, the API shall return `422 Unprocessable Entity`.
9. If a cursor was issued more than one hour ago, the API shall return `400 Bad Request`.
10. If a client provides an invalid `cursor`, the API shall return `400 Bad Request`.
11. If a client reuses a `cursor` with a different normalized filter set from the one used to obtain that cursor, the API shall return `400 Bad Request`.
12. If a client provides an invalid `limit`, the API shall return `400 Bad Request`.

## Data access and indexing

Acceptance criteria:

1. Where the endpoint supports case-insensitive filtering by up to 10 actor values and deterministic sorting by `timestamp` descending with `id` descending as the tie-breaker, the database schema shall include a composite index that supports actor-filtered retrieval in that sort order.
2. When the composite index for case-insensitive actor matching is implemented, it shall support case-insensitive actor lookups without requiring a full table scan.

## Validation and error handling

Acceptance criteria:

1. If `from` is not a valid UTC timestamp or date, the API shall return `400 Bad Request`.
2. If `to` is not a valid UTC timestamp or date, the API shall return `400 Bad Request`.
3. When `from` is provided as a date without a time, the API shall interpret it as the start of that day in UTC.
4. When `to` is provided as a date without a time, the API shall interpret it as the inclusive end of that day in UTC.
5. If `from` is later than `to`, the API shall return `400 Bad Request`.
6. If `actor` contains an empty supplied value, including a value that becomes empty after trimming, the API shall return `400 Bad Request`.
7. If `actor` contains more than 10 comma-separated values, the API shall return `422 Unprocessable Entity`.
8. If request validation fails for query parameters, the API shall not return partial results.
9. When the API returns `400 Bad Request` or `422 Unprocessable Entity`, the response body shall contain machine-readable `code`, a non-empty `message` string that identifies the invalid parameter or validation failure, and numeric `status` fields.

# Out of scope

- creating audit events
- updating audit events
- deleting audit events
- filtering by fields other than `actor`, `resource`, `from`, and `to`
- full-text search over `action` or `context`
- any write-side behavior, mutation path, or bulk correction workflow

## Resolved questions

- Actor normalization and multi-actor matching are resolved as trim-first, validate-before-dedup, case-insensitive exact matching with logical `OR` across actor values. See `design.md` sections `API contract` and `Validation rules`.
- Pagination strategy is resolved as opaque keyset pagination using deterministic `timestamp DESC, id DESC` ordering, one-hour cursor validity, and cursor binding to the normalized filter set. See `design.md` sections `Sort & determinism` and `Pagination strategy`.
- Invalid query responses are resolved to use machine-readable `400` or `422` bodies containing `code`, `message`, and `status`. See `design.md` section `API contract`.
