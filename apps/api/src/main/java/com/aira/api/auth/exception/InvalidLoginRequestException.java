package com.aira.api.auth.exception;

public final class InvalidLoginRequestException extends RuntimeException {
    private final String field;

    public InvalidLoginRequestException(String field) {
        super("Invalid login field: " + field);
        this.field = field;
    }

    public String getField() { return field; }
}
