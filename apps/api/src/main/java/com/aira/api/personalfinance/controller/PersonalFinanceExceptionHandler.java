package com.aira.api.personalfinance.controller;

import com.aira.api.personalfinance.exception.FinanceAccessRequiredException;
import com.aira.api.personalfinance.exception.FinanceConsentAlreadyActiveException;
import com.aira.api.personalfinance.exception.FinanceConsentNotFoundException;
import com.aira.api.personalfinance.exception.FinanceConsentRequiredException;
import com.aira.api.personalfinance.exception.FinanceReauthenticationFailedException;
import com.aira.api.personalfinance.exception.InvalidFinanceConsentException;
import com.aira.api.personalfinance.exception.InvalidPersonalFinanceRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps finance failures without disclosing credential or foreign-resource details. */
@RestControllerAdvice(basePackages = "com.aira.api.personalfinance")
public class PersonalFinanceExceptionHandler {
    @ExceptionHandler(FinanceReauthenticationFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public void reauthenticationFailed() {}

    @ExceptionHandler(FinanceAccessRequiredException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public void accessRequired() {}

    @ExceptionHandler({InvalidFinanceConsentException.class, InvalidPersonalFinanceRequestException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public void invalidRequest() {}

    @ExceptionHandler({FinanceConsentAlreadyActiveException.class, FinanceConsentRequiredException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public void consentConflict() {}

    // Foreign and missing consent IDs intentionally share the same response.
    @ExceptionHandler(FinanceConsentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public void consentNotFound() {}
}
