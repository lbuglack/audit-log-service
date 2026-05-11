DROP INDEX IF EXISTS idx_audit_events_actor;
DROP INDEX IF EXISTS idx_audit_events_resource;
DROP INDEX IF EXISTS idx_audit_events_timestamp_desc;
DROP INDEX IF EXISTS idx_audit_events_actor_timestamp;
DROP INDEX IF EXISTS idx_audit_events_resource_timestamp;

CREATE INDEX idx_audit_events_timestamp_id_desc ON audit_events (timestamp DESC, id DESC);

CREATE INDEX idx_audit_events_lower_actor_timestamp_id_desc
    ON audit_events (lower(actor), timestamp DESC, id DESC);

CREATE INDEX idx_audit_events_lower_resource_timestamp_id_desc
    ON audit_events (lower(resource), timestamp DESC, id DESC);

CREATE INDEX idx_audit_events_lower_actor_lower_resource_timestamp_id_desc
    ON audit_events (lower(actor), lower(resource), timestamp DESC, id DESC);
