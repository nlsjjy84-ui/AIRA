package com.aira.api.auth.email;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ResendEmailSenderTests {
    @Test
    void sendsVerificationAndPasswordResetThroughGatewayWithoutLoggingSecrets() {
        FakeGateway gateway = new FakeGateway();
        ResendEmailSender sender = new ResendEmailSender(
                gateway, "AIRA <no-reply@example.com>", "https://app.example.com");

        sender.sendRecoveryEmailVerification("user@example.com", "verification-token");
        assertEquals("Verify your AIRA recovery email", gateway.subject);
        assertTrue(gateway.html.contains("verification-token"));
        assertTrue(gateway.html.contains("/recovery-email/confirm"));

        sender.sendPasswordReset("user@example.com", "reset-token");
        assertEquals("Reset your AIRA password", gateway.subject);
        assertTrue(gateway.html.contains("reset-token"));
        assertTrue(gateway.html.contains("/password-reset/confirm"));
    }

    @Test
    void providerFailureIsSanitized() {
        ResendGateway gateway = (from, to, subject, html) -> {
            throw new RuntimeException("provider leaked re_sensitive_key");
        };
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new ResendEmailSender(gateway, "no-reply@example.com", "https://app.example.com")
                        .sendPasswordReset("user@example.com", "raw-sensitive-token"));
        assertEquals("Email delivery failed", failure.getMessage());
        assertFalse(failure.toString().contains("re_sensitive_key"));
        assertFalse(failure.toString().contains("raw-sensitive-token"));
        assertNull(failure.getCause());
    }

    static final class FakeGateway implements ResendGateway {
        String subject;
        String html;
        public void send(String from, String to, String subject, String html) {
            assertEquals("AIRA <no-reply@example.com>", from);
            assertEquals("user@example.com", to);
            this.subject = subject;
            this.html = html;
        }
    }
}
