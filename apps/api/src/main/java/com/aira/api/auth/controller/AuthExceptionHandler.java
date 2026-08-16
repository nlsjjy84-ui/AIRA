package com.aira.api.auth.controller;

import com.aira.api.auth.exception.InvalidSignupRequestException;
import com.aira.api.auth.exception.InvalidLoginRequestException;
import com.aira.api.auth.exception.AuthenticationFailedException;
import com.aira.api.auth.exception.NicknameAlreadyExistsException;
import com.aira.api.auth.exception.InvalidPasswordResetTokenException;
import com.aira.api.auth.exception.InvalidPasswordResetRequestException;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {
        AuthController.class, RecoveryEmailController.class, PasswordResetController.class})
public class AuthExceptionHandler {
    @ExceptionHandler(AuthenticationFailedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse authenticationFailed() {
        return new ErrorResponse("AUTHENTICATION_FAILED", "닉네임 또는 비밀번호를 확인해주세요.", null);
    }

    @ExceptionHandler(InvalidLoginRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidLogin(InvalidLoginRequestException exception) {
        return invalid(List.of(new FieldError(exception.getField(), "유효하지 않은 값입니다.")));
    }

    @ExceptionHandler(NicknameAlreadyExistsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse duplicateNickname() {
        return new ErrorResponse("NICKNAME_ALREADY_EXISTS", "사용할 수 없는 닉네임입니다.", null);
    }

    @ExceptionHandler(InvalidSignupRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidSignup(InvalidSignupRequestException exception) {
        return invalid(List.of(new FieldError(exception.getField(), "유효하지 않은 값입니다.")));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidBean(MethodArgumentNotValidException exception) {
        List<FieldError> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldError(error.getField(), "필수 값입니다."))
                .toList();
        return invalid(errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse unreadableBody() {
        return invalid(List.of(new FieldError("request", "요청 본문을 확인해주세요.")));
    }

    @ExceptionHandler(InvalidPasswordResetTokenException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidPasswordResetToken() {
        return new ErrorResponse("INVALID_PASSWORD_RESET_TOKEN",
                "Password reset token is invalid or expired.", null);
    }

    @ExceptionHandler(InvalidPasswordResetRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse invalidPasswordResetRequest() {
        return invalid(List.of(new FieldError("newPassword", "Invalid value.")));
    }

    private static ErrorResponse invalid(List<FieldError> errors) {
        return new ErrorResponse("INVALID_REQUEST", "입력값을 확인해주세요.", errors);
    }

    record ErrorResponse(String code, String message, List<FieldError> errors) {}
    record FieldError(String field, String message) {}
}
