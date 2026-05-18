package com.auditlog;

import com.auditlog.dto.response.ApiErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuditLogApiExceptionHandler {

    @ExceptionHandler(InvalidQueryException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidQuery(InvalidQueryException exception) {
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiErrorResponse(
                        exception.getCode(),
                        exception.getMessage(),
                        exception.getStatus().value()));
    }
}
