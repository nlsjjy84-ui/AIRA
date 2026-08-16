package com.aira.api.auth.exception;

public final class InvalidPasswordResetRequestException extends RuntimeException {
    public InvalidPasswordResetRequestException() { super("Password reset request is invalid"); }
}
