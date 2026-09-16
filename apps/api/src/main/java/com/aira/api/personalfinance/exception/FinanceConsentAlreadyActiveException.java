package com.aira.api.personalfinance.exception;

public final class FinanceConsentAlreadyActiveException extends RuntimeException {
    public FinanceConsentAlreadyActiveException() {
        super("Finance consent is already active");
    }
}
