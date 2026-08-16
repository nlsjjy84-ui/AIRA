package com.aira.api.auth.service;

import com.aira.api.auth.email.EmailSender;
import com.aira.api.auth.exception.*;
import com.aira.api.auth.security.*;
import com.aira.api.user.repository.PasswordResetStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class PasswordResetService {
    private final RecoveryEmailProtector emails;
    private final VerificationTokenGenerator tokens;
    private final SessionTokenHasher tokenHasher;
    private final PasswordHasher passwordHasher;
    private final PasswordResetStore store;
    private final EmailSender sender;
    private final Clock clock;

    public PasswordResetService(RecoveryEmailProtector emails, VerificationTokenGenerator tokens,
            SessionTokenHasher tokenHasher, PasswordHasher passwordHasher,
            PasswordResetStore store, EmailSender sender, Clock clock) {
        this.emails = emails;
        this.tokens = tokens;
        this.tokenHasher = tokenHasher;
        this.passwordHasher = passwordHasher;
        this.store = store;
        this.sender = sender;
        this.clock = clock;
    }

    public void request(String email) {
        RecoveryEmailLookup lookup = emails.lookup(email);
        String rawToken = tokens.generate();
        OffsetDateTime now = OffsetDateTime.now(clock);
        if (store.create(lookup.lookupHash(), tokenHasher.hash(rawToken), now, now.plusMinutes(30))) {
            sender.sendPasswordReset(lookup.normalizedEmail(), rawToken);
        }
    }

    public void confirm(String rawToken, String newPassword) {
        final String passwordHash;
        try {
            passwordHash = passwordHasher.hash(newPassword);
        } catch (IllegalArgumentException exception) {
            throw new InvalidPasswordResetRequestException();
        }
        if (rawToken == null || rawToken.isBlank()
                || !store.consumeAndReset(tokenHasher.hash(rawToken), passwordHash,
                        OffsetDateTime.now(clock))) {
            throw new InvalidPasswordResetTokenException();
        }
    }
}
