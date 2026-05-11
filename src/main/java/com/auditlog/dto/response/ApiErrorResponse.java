package com.auditlog.dto.response;

public record ApiErrorResponse(String code, String message, int status) {}
