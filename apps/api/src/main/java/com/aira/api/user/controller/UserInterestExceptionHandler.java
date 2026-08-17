package com.aira.api.user.controller;

import com.aira.api.user.exception.DuplicateUserInterestException;
import com.aira.api.user.exception.InterestEntityNotFoundException;
import com.aira.api.user.exception.InvalidInterestEntityException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = UserInterestController.class)
public class UserInterestExceptionHandler {
    @ExceptionHandler(InterestEntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    ErrorResponse entityNotFound() {
        return new ErrorResponse("ENTITY_NOT_FOUND", "Entity was not found");
    }

    @ExceptionHandler(InvalidInterestEntityException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidEntity(InvalidInterestEntityException exception) {
        return new ErrorResponse("INVALID_INTEREST_ENTITY", exception.getMessage());
    }

    @ExceptionHandler(DuplicateUserInterestException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse duplicateInterest() {
        return new ErrorResponse("INTEREST_ALREADY_EXISTS", "Interest already exists");
    }

    record ErrorResponse(String code, String message) {}
}
