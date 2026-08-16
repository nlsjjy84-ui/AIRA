package com.aira.api.auth.exception;

public final class InvalidPasswordResetTokenException extends RuntimeException {
    public InvalidPasswordResetTokenException() { super("Password reset token is invalid"); }
}
