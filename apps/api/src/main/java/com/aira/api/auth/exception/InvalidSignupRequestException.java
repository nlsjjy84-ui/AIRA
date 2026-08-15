package com.aira.api.auth.exception;

public final class InvalidSignupRequestException extends RuntimeException {
    private final String field;

    public InvalidSignupRequestException(String field) {
        super("Invalid signup field: " + field);
        this.field = field;
    }

    public String getField() { return field; }
}
