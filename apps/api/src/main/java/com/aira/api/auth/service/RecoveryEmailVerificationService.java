package com.aira.api.auth.service;

import com.aira.api.auth.email.EmailSender;
import com.aira.api.auth.security.*;
import com.aira.api.user.repository.RecoveryEmailVerificationStore;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RecoveryEmailVerificationService {
    private final RecoveryEmailProtector protector;
    private final VerificationTokenGenerator tokens;
    private final SessionTokenHasher tokenHasher;
    private final RecoveryEmailVerificationStore store;
    private final EmailSender sender;
    private final Clock clock;

    public RecoveryEmailVerificationService(RecoveryEmailProtector protector,
            VerificationTokenGenerator tokens, SessionTokenHasher tokenHasher,
            RecoveryEmailVerificationStore store, EmailSender sender, Clock clock) {
        this.protector = protector;
        this.tokens = tokens;
        this.tokenHasher = tokenHasher;
        this.store = store;
        this.sender = sender;
        this.clock = clock;
    }

    public void request(UUID userId, String email) {
        if (userId == null) throw new IllegalArgumentException("Authenticated user is required");
        ProtectedRecoveryEmail protectedEmail = protector.protect(email);
        String rawToken = tokens.generate();
        OffsetDateTime now = OffsetDateTime.now(clock);
        store.create(userId, protectedEmail.ciphertext(), protectedEmail.lookupHash(),
                protectedEmail.keyVersion(), tokenHasher.hash(rawToken), now, now.plusMinutes(30));
        sender.sendRecoveryEmailVerification(protectedEmail.normalizedEmail(), rawToken);
    }

    public boolean confirm(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return false;
        try {
            return store.confirm(tokenHasher.hash(rawToken), OffsetDateTime.now(clock));
        } catch (org.springframework.dao.DuplicateKeyException conflict) {
            // The store transaction has rolled back; never reveal another account's email.
            return false;
        }
    }
}
