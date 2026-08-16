package com.aira.api.user.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface RecoveryEmailVerificationStore {
    void create(UUID userId, byte[] ciphertext, byte[] lookupHash, short keyVersion,
            byte[] tokenHash, OffsetDateTime requestedAt, OffsetDateTime expiresAt);
    boolean confirm(byte[] tokenHash, OffsetDateTime verifiedAt);
}
