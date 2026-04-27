package com.auditlog.facade;

import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.response.AuditEventResponse;
import com.auditlog.service.AuditEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuditEventFacade {

    private final AuditEventService auditEventService;

    public AuditEventResponse create(CreateAuditEventRequest request) {
        return auditEventService.create(request);
    }

    public List<AuditEventResponse> search(String actor, String resource, Instant from, Instant to) {
        return auditEventService.search(actor, resource, from, to);
    }
}
