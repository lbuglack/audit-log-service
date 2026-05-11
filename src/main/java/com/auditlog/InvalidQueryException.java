package com.auditlog;

public class InvalidQueryException extends RuntimeException {

    private final String code;

    public InvalidQueryException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
