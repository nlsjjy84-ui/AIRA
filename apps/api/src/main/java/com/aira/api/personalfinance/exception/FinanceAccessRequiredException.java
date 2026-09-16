package com.aira.api.personalfinance.exception;

/** Raised when a sensitive finance operation lacks a valid step-up grant. */
public final class FinanceAccessRequiredException extends RuntimeException {
    public FinanceAccessRequiredException() {
        super("Finance step-up access is required");
    }
}
