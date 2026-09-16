package com.aira.api.personalfinance.exception;

public class FinanceConsentRequiredException extends RuntimeException {
    public FinanceConsentRequiredException() {
        super("Active finance consent is required");
    }
}
