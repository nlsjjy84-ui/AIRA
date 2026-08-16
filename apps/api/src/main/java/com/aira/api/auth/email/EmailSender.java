package com.aira.api.auth.email;

public interface EmailSender {
    void sendRecoveryEmailVerification(String email, String rawToken);

    default void sendPasswordReset(String email, String rawToken) {
        throw new IllegalStateException("Password reset email sender is not configured");
    }
}
