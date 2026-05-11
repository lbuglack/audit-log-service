package com.auditlog;

import java.time.Instant;
import java.util.UUID;

public record AuditEventQueryCursor(
        int version, Instant issuedAt, Instant lastTimestamp, UUID lastId, String filterFingerprint, Integer limit) {}
