package com.aira.api.market.exception;

public class ExternalIdentifierConflictException extends RuntimeException {
    public ExternalIdentifierConflictException() {
        super("External identifier is already assigned to another entity");
    }
}
