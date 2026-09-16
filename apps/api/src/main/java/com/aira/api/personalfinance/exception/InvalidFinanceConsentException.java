package com.aira.api.personalfinance.exception;

public final class InvalidFinanceConsentException extends RuntimeException {
    public InvalidFinanceConsentException() {
        super("Invalid finance consent");
    }
}
