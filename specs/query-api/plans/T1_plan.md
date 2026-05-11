# T1 Plan: Refactor the Query API Boundary to the Approved Contract

## Summary

Implement the approved `GET /audit-events` boundary without changing write-side behavior. The query endpoint must move from the current bare `List<AuditEventResponse>` response to a paginated envelope with `items` and optional `nextCursor`, while keeping the existing controller -> facade -> service -> dao/repository layering.

## Implementation Changes

- Introduce dedicated query DTOs separate from the create-event DTOs:
  - a request model that carries raw query inputs for `actor`, `resource`, `from`, `to`, `cursor`, and `limit`
  - a response envelope model with `items` and optional `nextCursor`
  - a query item response model that preserves the approved flat event fields: `id`, `timestamp`, `actor`, `action`, `resource`, `outcome`, and `context`
- Update `AuditEventController` so `GET /audit-events` returns the response envelope instead of `List<AuditEventResponse>`.
- Update `AuditEventFacade` and `AuditEventService` to accept a single query request model and return the new paginated response model.
- Keep `POST /audit-events` unchanged and continue using the existing create request/response DTOs for write-side behavior.
- Preserve entity-to-response mapping inside the service layer so controllers still do not expose entities directly.

## Public Interfaces

- `GET /audit-events` accepts:
  - `actor`
  - `resource`
  - `from`
  - `to`
  - `cursor`
  - `limit`
- `GET /audit-events` returns:

```json
{
  "items": [
    {
      "id": "uuid",
      "timestamp": "2026-04-17T11:02:14Z",
      "actor": "user:42",
      "action": "resource.updated",
      "resource": "project:1",
      "outcome": "success",
      "context": null
    }
  ],
  "nextCursor": "opaque-cursor"
}
```

- `nextCursor` is omitted when there is no next page.

## Test Plan

- Update query controller integration tests to assert the envelope shape instead of a bare array.
- Verify `GET /audit-events` still supports no filters, actor-only, resource-only, combined filters, and time-range filters through the new response model.
- Verify `POST /audit-events` integration tests remain unchanged and continue to pass.

## Assumptions

- No compatibility layer is kept for the old bare-array query response.
- Query DTOs are separate from create DTOs so the read contract can evolve independently.
- Query parsing and validation details are handled in T2, but the boundary in T1 must already expose the final approved inputs and outputs.
