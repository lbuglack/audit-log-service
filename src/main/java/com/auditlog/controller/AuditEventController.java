package com.auditlog.controller;

import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.request.SearchAuditEventsRequest;
import com.auditlog.dto.response.AuditEventResponse;
import com.auditlog.dto.response.SearchAuditEventsResponse;
import com.auditlog.facade.AuditEventFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/audit-events")
@RequiredArgsConstructor
public class AuditEventController {

    private final AuditEventFacade auditEventFacade;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AuditEventResponse create(@Valid @RequestBody CreateAuditEventRequest request) {
        return auditEventFacade.create(request);
    }

    @GetMapping
    public SearchAuditEventsResponse search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String limit) {
        return auditEventFacade.search(new SearchAuditEventsRequest(actor, resource, from, to, cursor, limit));
    }
}
