package com.aira.api.user.repository;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PostgresSessionLogoutRepository implements SessionLogoutRepository {
    private static final String REVOKE_FOR_LOGOUT = """
            UPDATE user_session
            SET revoked_at = ?, revoke_reason = 'LOGOUT'
            WHERE token_hash = ?
              AND revoked_at IS NULL
            """;

    private final JdbcTemplate jdbc;

    public PostgresSessionLogoutRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void revokeForLogout(byte[] tokenHash, OffsetDateTime revokedAt) {
        if (tokenHash == null || tokenHash.length == 0 || revokedAt == null) return;
        jdbc.update(REVOKE_FOR_LOGOUT, revokedAt, tokenHash.clone());
    }
}
