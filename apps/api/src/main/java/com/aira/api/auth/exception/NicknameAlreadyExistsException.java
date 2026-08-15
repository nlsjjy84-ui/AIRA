package com.aira.api.auth.exception;

public final class NicknameAlreadyExistsException extends RuntimeException {
    public NicknameAlreadyExistsException() {
        super("Nickname already exists");
    }
}
