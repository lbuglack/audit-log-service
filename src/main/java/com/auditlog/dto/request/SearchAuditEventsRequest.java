package com.auditlog.dto.request;

public record SearchAuditEventsRequest(
        String actor,
        String resource,
        String from,
        String to,
        String cursor,
        String limit) {}
