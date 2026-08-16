package com.aira.api.auth.email;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;

public final class SdkResendGateway implements ResendGateway {
    private final Resend resend;

    public SdkResendGateway(Resend resend) { this.resend = resend; }

    @Override
    public void send(String from, String to, String subject, String html) {
        CreateEmailOptions options = CreateEmailOptions.builder()
                .from(from).to(to).subject(subject).html(html).build();
        try {
            resend.emails().send(options);
        } catch (ResendException exception) {
            throw new IllegalStateException("Resend request failed");
        }
    }
}
