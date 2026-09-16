package com.aira.api.personalfinance.exception;

public final class FinanceConsentNotFoundException extends RuntimeException {
    public FinanceConsentNotFoundException() {
        super("Finance consent was not found");
    }
}
