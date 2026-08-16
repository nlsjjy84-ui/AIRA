package com.aira.api.auth.email;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class ResendEmailSender implements EmailSender {
    private final ResendGateway gateway;
    private final String from;
    private final String publicBaseUrl;

    public ResendEmailSender(ResendGateway gateway, String from, String publicBaseUrl) {
        if (gateway == null || from == null || from.isBlank()) {
            throw new IllegalStateException("Resend email configuration is invalid");
        }
        URI base = URI.create(publicBaseUrl == null ? "" : publicBaseUrl);
        if (!"https".equalsIgnoreCase(base.getScheme()) || base.getHost() == null) {
            throw new IllegalStateException("Resend public URL must use HTTPS");
        }
        this.gateway = gateway;
        this.from = from;
        this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
    }

    @Override
    public void sendRecoveryEmailVerification(String email, String rawToken) {
        send(email, "Verify your AIRA recovery email",
                "/recovery-email/confirm", rawToken, "Verify recovery email");
    }

    @Override
    public void sendPasswordReset(String email, String rawToken) {
        send(email, "Reset your AIRA password",
                "/password-reset/confirm", rawToken, "Reset password");
    }

    private void send(String email, String subject, String path, String rawToken, String action) {
        if (email == null || email.isBlank() || rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Valid email delivery values are required");
        }
        String link = publicBaseUrl + path + "?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
        String html = "<p>" + action + ":</p><p><a href=\"" + link + "\">" + action + "</a></p>";
        try {
            gateway.send(from, email, subject, html);
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Email delivery failed");
        }
    }
}
