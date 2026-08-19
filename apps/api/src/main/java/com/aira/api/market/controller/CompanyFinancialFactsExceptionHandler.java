package com.aira.api.market.controller;

import com.aira.api.market.query.CompanyFinancialFactsQueryException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = CompanyFinancialFactsController.class)
public class CompanyFinancialFactsExceptionHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse malformedParameter() {
        return new ErrorResponse("MALFORMED_PARAMETER", "A request parameter is malformed");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidRequest(IllegalArgumentException exception) {
        return new ErrorResponse("INVALID_EXACT_PERIOD", exception.getMessage());
    }

    @ExceptionHandler(CompanyFinancialFactsQueryException.class)
    org.springframework.http.ResponseEntity<ErrorResponse> queryFailure(
            CompanyFinancialFactsQueryException exception) {
        return switch (exception.category()) {
            case COMPANY_NOT_FOUND -> response(HttpStatus.NOT_FOUND,
                    "COMPANY_NOT_FOUND", exception.getMessage());
            case FACTS_NOT_FOUND -> response(HttpStatus.NOT_FOUND,
                    "FACTS_NOT_FOUND", exception.getMessage());
            case UNSUPPORTED_PREDICATE -> response(HttpStatus.BAD_REQUEST,
                    "UNSUPPORTED_PREDICATE", exception.getMessage());
            case INCONSISTENT_PROVENANCE -> response(HttpStatus.CONFLICT,
                    "INCONSISTENT_PROVENANCE", exception.getMessage());
        };
    }

    private static org.springframework.http.ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String message) {
        return org.springframework.http.ResponseEntity.status(status)
                .body(new ErrorResponse(code, message));
    }

    record ErrorResponse(String code, String message) {}
}
