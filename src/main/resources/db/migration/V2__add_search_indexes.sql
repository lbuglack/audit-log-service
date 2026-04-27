-- actor: equality filter
CREATE INDEX idx_audit_events_actor ON audit_events (actor);

-- resource: equality filter
CREATE INDEX idx_audit_events_resource ON audit_events (resource);

-- timestamp DESC: time-range filter + default sort order
CREATE INDEX idx_audit_events_timestamp_desc ON audit_events (timestamp DESC);

-- composite: actor + time range (common combined search)
CREATE INDEX idx_audit_events_actor_timestamp ON audit_events (actor, timestamp DESC);

-- composite: resource + time range
CREATE INDEX idx_audit_events_resource_timestamp ON audit_events (resource, timestamp DESC);
