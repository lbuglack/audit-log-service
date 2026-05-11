package com.auditlog.facade;

import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.request.SearchAuditEventsRequest;
import com.auditlog.dto.response.AuditEventResponse;
import com.auditlog.dto.response.SearchAuditEventsResponse;
import com.auditlog.service.AuditEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuditEventFacade {

    private final AuditEventService auditEventService;

    public AuditEventResponse create(CreateAuditEventRequest request) {
        return auditEventService.create(request);
    }

    public SearchAuditEventsResponse search(SearchAuditEventsRequest request) {
        return auditEventService.search(request);
    }
}
