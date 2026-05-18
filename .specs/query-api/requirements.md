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

1. Given audit events exist for multiple actors, when `actor` is provided as a comma-separated list, then only events whose actor exactly matches at least one supplied actor value are returned, using case-insensitive comparison.
2. Given `actor` is provided with whitespace around non-empty values, when the request is executed, then leading and trailing whitespace around each supplied actor value is ignored before matching.
3. Given `actor` contains between 1 and 10 supplied comma-separated values and each supplied value remains non-empty after trimming, when the request is executed, then the actor filter is accepted and applied with logical `OR` across the supplied actor values.
4. Given `actor`, `resource`, `from`, or `to` are provided together, when the request is executed, then an event is returned only if it matches at least one supplied actor value and satisfies every other supplied filter.
5. Given audit events exist for multiple resources, when `resource` is provided, then only events with the exact case-insensitive matching resource are returned.
6. Given `resource` is provided as an empty string, when the request is executed, then that filter is ignored.
7. Given no filters are provided, when the request is executed, then the API returns all records sorted by `timestamp` descending with `id` descending as the tie-breaker.
8. Given `from` and `to` are provided, when the request is executed, then only events with `timestamp >= from` and `timestamp <= to` are returned.
9. Given only `from` is provided, when the request is executed, then only events with `timestamp >= from` are returned.
10. Given only `to` is provided, when the request is executed, then only events with `timestamp <= to` are returned.
11. Given matching events exist, when the request succeeds, then the response contains an `items` array and each returned event contains `id`, `timestamp`, `actor`, `action`, `resource`, `outcome`, and `context`.
12. Given a returned event has no context data, when the request succeeds, then `context` may be `null`.
13. Given no events match the filters, when the request succeeds, then the API returns an empty `items` array.

## SRE

As an SRE, I want to reconstruct the timeline of actions on a resource during an incident so that I can understand what happened and in what order.

Acceptance criteria:

1. Given multiple matching events exist for the same resource, when the request is executed, then the events are returned sorted by `timestamp` descending, with `id` descending as the tie-breaker.
2. Given a time-bounded incident investigation, when `resource`, `from`, and `to` are supplied, then the API returns only events for that resource in the inclusive time window.
3. Given the endpoint is read-only, when an SRE queries audit data, then no audit records are created, updated, or deleted.
4. Given an event is returned, when the SRE inspects the result, then the response reflects stored audit data and does not omit the actor, resource, action, or timestamp information needed for timeline reconstruction.

## Security analyst

As a Security Analyst, I want to paginate through a large multi-actor result set without loss or duplication so that I can investigate related identities across high-volume audit history reliably.

Acceptance criteria:

1. Given more matching records exist than fit in one page, when the client requests `limit=50` while filtering by one or more actor values, then the API returns no more than 50 events in that page.
2. Given the client omits `limit`, when the request is executed, then the API uses the default page size of `50`.
3. Given a client requests paginated results using `cursor` together with `actor`, `resource`, `from`, and `to`, when successive page requests are made for the same mixed filter set, then records are not skipped or duplicated between pages.
4. Given more matching records remain after the current page, when the request succeeds, then the response includes `nextCursor`.
5. Given no further matching records remain after the current page, when the request succeeds, then the response omits `nextCursor`.
6. Given multiple matching events share the same `timestamp`, when paginated results are returned for a multi-actor query, then the events are sorted by `timestamp` descending with `id` descending as the tie-breaker.
7. Given `actor` contains repeated values, when the request is executed, then each supplied value still counts toward the maximum number of actor values allowed in one request.
8. Given `actor` contains 11 comma-separated values, when the request is executed, then the API returns `422 Unprocessable Entity`.
9. Given a cursor was issued more than one hour ago, when the request is executed, then the API returns `400 Bad Request`.
10. Given a client provides an invalid `cursor`, when the request is executed, then the API returns `400 Bad Request`.
11. Given a client provides an invalid `limit`, when the request is executed, then the API returns `400 Bad Request`.

## Data access and indexing

Acceptance criteria:

1. Given the endpoint supports case-insensitive filtering by up to 10 actor values and deterministic sorting by `timestamp` descending with `id` descending as the tie-breaker, when the database schema is defined, then it must include a composite index that supports actor-filtered retrieval in that sort order.
2. Given actor matching is case-insensitive, when the composite index is implemented, then it must support case-insensitive actor lookups without requiring a full table scan.

## Validation and error handling

Acceptance criteria:

1. Given `from` is not a valid UTC timestamp or date, when the request is executed, then the API returns `400 Bad Request`.
2. Given `to` is not a valid UTC timestamp or date, when the request is executed, then the API returns `400 Bad Request`.
3. Given `from` is provided as a date without a time, when the request is executed, then the API interprets it as the start of that day in UTC.
4. Given `to` is provided as a date without a time, when the request is executed, then the API interprets it as the inclusive end of that day in UTC.
5. Given `from` is later than `to`, when the request is executed, then the API returns `400 Bad Request`.
6. Given `actor` contains an empty supplied value, including a value that becomes empty after trimming, when the request is executed, then the API returns `400 Bad Request`.
7. Given `actor` contains more than 10 comma-separated values, when the request is executed, then the API returns `422 Unprocessable Entity`.
8. Given the request contains invalid query parameters, when validation fails, then the API does not return partial results.
9. Given the API returns `400 Bad Request` or `422 Unprocessable Entity`, when the client inspects the response, then the body contains machine-readable `code`, a non-empty `message` string that identifies the invalid parameter or validation failure, and numeric `status` fields.

# Out of scope

- creating audit events
- updating audit events
- deleting audit events
- filtering by fields other than `actor`, `resource`, `from`, and `to`
- full-text search over `action` or `context`
- any write-side behavior, mutation path, or bulk correction workflow

# Open questions

- None currently.
