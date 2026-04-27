package com.auditlog.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.Map;

public record CreateAuditEventRequest(

        @NotBlank
        String actor,

        @NotBlank
        String action,

        @NotBlank
        String resource,

        @NotNull
        @Pattern(regexp = "success|denied|error")
        String outcome,

        Map<String, Object> context
) {}
