package com.auditlog.dto.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        Instant timestamp,
        String actor,
        String action,
        String resource,
        String outcome,
        Map<String, Object> context
) {}
