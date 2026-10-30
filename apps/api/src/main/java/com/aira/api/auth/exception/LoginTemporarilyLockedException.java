package com.aira.api.auth.exception;

/** 같은 닉네임으로 로그인을 너무 많이 틀려 잠시 막혔을 때. */
public final class LoginTemporarilyLockedException extends RuntimeException {
    private final long retryAfterSeconds;

    public LoginTemporarilyLockedException(long retryAfterSeconds) {
        super("Too many failed login attempts");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
