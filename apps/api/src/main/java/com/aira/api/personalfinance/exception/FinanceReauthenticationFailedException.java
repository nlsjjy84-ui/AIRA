package com.aira.api.personalfinance.exception;

/** Generic step-up failure: never reveal whether password, session, or account state failed. */
public final class FinanceReauthenticationFailedException extends RuntimeException {
    public FinanceReauthenticationFailedException() {
        super("Finance reauthentication failed");
    }
}
