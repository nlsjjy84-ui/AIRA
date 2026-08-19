package com.aira.api.market.controller;

import com.aira.api.market.query.PublicCompanyCatalogException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice(assignableTypes = PublicCompanyCatalogController.class)
public class PublicCompanyCatalogExceptionHandler {
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorResponse> malformedParameter() {
        return ResponseEntity.badRequest()
                .body(new ErrorResponse("MALFORMED_PARAMETER", "A request parameter is malformed"));
    }

    @ExceptionHandler(PublicCompanyCatalogException.class)
    ResponseEntity<ErrorResponse> queryFailure(PublicCompanyCatalogException exception) {
        return switch (exception.category()) {
            case COMPANY_NOT_FOUND -> response(HttpStatus.NOT_FOUND,
                    "COMPANY_NOT_FOUND", exception.getMessage());
            case PERIODS_NOT_FOUND -> response(HttpStatus.NOT_FOUND,
                    "PERIODS_NOT_FOUND", exception.getMessage());
        };
    }

    private static ResponseEntity<ErrorResponse> response(
            HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ErrorResponse(code, message));
    }

    record ErrorResponse(String code, String message) {}
}
