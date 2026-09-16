package com.aira.api.personalfinance.exception;

public class InvalidPersonalFinanceRequestException extends RuntimeException {
    public InvalidPersonalFinanceRequestException() {
        super("Invalid personal finance request");
    }
}
