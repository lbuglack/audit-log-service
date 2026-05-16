package com.auditlog.service;

import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.request.SearchAuditEventsRequest;
import com.auditlog.dto.response.AuditEventResponse;
import com.auditlog.dto.response.SearchAuditEventsResponse;

public interface AuditEventService {

    AuditEventResponse create(CreateAuditEventRequest request);

    SearchAuditEventsResponse search(SearchAuditEventsRequest request);
}
