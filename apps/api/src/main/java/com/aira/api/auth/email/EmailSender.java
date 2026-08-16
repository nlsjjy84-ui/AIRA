package com.aira.api.auth.email;

public interface EmailSender {
    void sendRecoveryEmailVerification(String email, String rawToken);
}
