package com.aira.api.user.repository;

import java.time.OffsetDateTime;

public interface SessionLogoutRepository {
    void revokeForLogout(byte[] tokenHash, OffsetDateTime revokedAt);
}
