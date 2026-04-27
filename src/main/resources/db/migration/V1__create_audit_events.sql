CREATE TABLE audit_events (
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    timestamp TIMESTAMPTZ NOT NULL,
    actor     TEXT        NOT NULL,
    action    TEXT        NOT NULL,
    resource  TEXT        NOT NULL,
    outcome   TEXT        NOT NULL CHECK (outcome IN ('success', 'denied', 'error')),
    context   JSONB
);
