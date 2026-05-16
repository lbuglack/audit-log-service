package com.auditlog;

import com.auditlog.dto.response.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class AuditLogApiExceptionHandler {

    @ExceptionHandler(InvalidQueryException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrorResponse handleInvalidQuery(InvalidQueryException exception) {
        return new ApiErrorResponse(exception.getCode(), exception.getMessage(), HttpStatus.BAD_REQUEST.value());
    }
}
