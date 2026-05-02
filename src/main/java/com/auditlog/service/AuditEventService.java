package com.auditlog.service;

import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.response.AuditEventResponse;
import java.time.Instant;
import java.util.List;

public interface AuditEventService {

    AuditEventResponse create(CreateAuditEventRequest request);

    List<AuditEventResponse> search(String actor, String resource, Instant from, Instant to);
}
