# Problem

The system needs a read-only HTTP endpoint that allows internal users to retrieve audit events by actor, resource, and time range without changing stored data.

The endpoint must let internal users:

- retrieve audit events for a specific actor
- retrieve audit events for a specific resource
- retrieve audit events within a time range
- combine filters in a single request
- page through large result sets safely
- query audit data without mutating stored events

# User stories with AC

## Compliance officer

As a Compliance Officer, I want to retrieve audit events for a specific actor, resource, and time range so that I can confirm or refute whether a specific action happened during an audit.

Acceptance criteria:

1. Given audit events exist for multiple actors, when `actor` is provided, then only events with the exact case-insensitive matching actor are returned.
2. Given audit events exist for multiple resources, when `resource` is provided, then only events with the exact case-insensitive matching resource are returned.
3. Given `actor` or `resource` is provided, when the request is executed, then matching is exact and case-insensitive.
4. Given both `actor` and `resource` are provided, when the request is executed, then both filters are applied with logical `AND`.
5. Given `actor` or `resource` is provided as an empty string, when the request is executed, then that filter is ignored.
6. Given no filters are provided, when the request is executed, then the API returns all records sorted by newest `timestamp` first.
7. Given `from` and `to` are provided, when the request is executed, then only events with `timestamp >= from` and `timestamp <= to` are returned.
8. Given only `from` is provided, when the request is executed, then only events with `timestamp >= from` are returned.
9. Given only `to` is provided, when the request is executed, then only events with `timestamp <= to` are returned.
10. Given matching events exist, when the request succeeds, then the response contains an `items` array and each returned event contains `id`, `timestamp`, `actor`, `action`, `resource`, `outcome`, and `context`.
11. Given a returned event has no context data, when the request succeeds, then `context` may be `null`.
12. Given no events match the filters, when the request succeeds, then the API returns an empty `items` array.

## SRE

As an SRE, I want to reconstruct the timeline of actions on a resource during an incident so that I can understand what happened and in what order.

Acceptance criteria:

1. Given multiple matching events exist for the same resource, when the request is executed, then the events are returned sorted by `timestamp`.
2. Given a time-bounded incident investigation, when `resource`, `from`, and `to` are supplied, then the API returns only events for that resource in the inclusive time window.
3. Given the endpoint is read-only, when an SRE queries audit data, then no audit records are created, updated, or deleted.
4. Given an event is returned, when the SRE inspects the result, then the response reflects stored audit data and does not omit the actor, resource, action, or timestamp information needed for timeline reconstruction.

## Security analyst

As a Security Analyst, I want to paginate through a large result set without loss or duplication so that I can review high-volume audit history reliably.

Acceptance criteria:

1. Given more matching records exist than fit in one page, when the client requests `limit=50`, then the API returns no more than 50 events in that page.
2. Given the client omits `limit`, when the request is executed, then the API uses the default page size of `50`.
3. Given a client requests paginated results using `cursor`, when successive page requests are made, then records are not skipped or duplicated between pages.
4. Given more matching records remain after the current page, when the request succeeds, then the response includes `nextCursor`.
5. Given no further matching records remain after the current page, when the request succeeds, then the response omits `nextCursor`.
6. Given a cursor was issued more than one hour ago, when the request is executed, then the API returns `400 Bad Request`.
7. Given a client provides an invalid `cursor`, when the request is executed, then the API returns `400 Bad Request`.
8. Given a client provides an invalid `limit`, when the request is executed, then the API returns `400 Bad Request`.

## Validation and error handling

Acceptance criteria:

1. Given `from` is not a valid UTC timestamp or date, when the request is executed, then the API returns `400 Bad Request`.
2. Given `to` is not a valid UTC timestamp or date, when the request is executed, then the API returns `400 Bad Request`.
3. Given `from` is provided as a date without a time, when the request is executed, then the API interprets it as the start of that day in UTC.
4. Given `to` is provided as a date without a time, when the request is executed, then the API interprets it as the inclusive end of that day in UTC.
5. Given `from` is later than `to`, when the request is executed, then the API returns `400 Bad Request`.
6. Given the request contains invalid query parameters, when validation fails, then the API does not return partial results.
7. Given the API returns `400 Bad Request`, when the client inspects the response, then the body contains machine-readable `code`, human-readable `message`, and numeric `status` fields.

# Out of scope

- creating audit events
- updating audit events
- deleting audit events
- filtering by fields other than `actor`, `resource`, `from`, and `to`
- full-text search over `action` or `context`
- any write-side behavior, mutation path, or bulk correction workflow

# Open questions

- None currently.
