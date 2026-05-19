package com.auditlog;

import org.springframework.http.HttpStatus;

public class InvalidQueryException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public InvalidQueryException(String code, String message) {
        this(HttpStatus.BAD_REQUEST, code, message);
    }

    public InvalidQueryException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
