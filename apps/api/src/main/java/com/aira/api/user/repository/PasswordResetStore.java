package com.aira.api.user.repository;

import java.time.OffsetDateTime;

public interface PasswordResetStore {
    boolean create(byte[] emailLookupHash, byte[] tokenHash,
            OffsetDateTime requestedAt, OffsetDateTime expiresAt);
    boolean consumeAndReset(byte[] tokenHash, String passwordHash, OffsetDateTime usedAt);
}
