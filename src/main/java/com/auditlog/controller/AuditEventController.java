package com.auditlog.controller;

import com.auditlog.dto.request.CreateAuditEventRequest;
import com.auditlog.dto.response.AuditEventResponse;
import com.auditlog.facade.AuditEventFacade;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

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
    public List<AuditEventResponse> search(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to
    ) {
        return auditEventFacade.search(actor, resource, from, to);
    }
}
