package com.aira.api.user.repository;

import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresPasswordResetStore implements PasswordResetStore {
    private static final String INVALIDATE_ACTIVE = """
            UPDATE password_reset_token AS token
            SET invalidated_at = ?, invalid_reason = 'REPLACED'
            FROM recovery_email AS email
            WHERE token.recovery_email_id = email.id
              AND email.email_lookup_hash = ?
              AND email.deleted_at IS NULL
              AND token.used_at IS NULL
              AND token.invalidated_at IS NULL
            """;
    private static final String CREATE = """
            INSERT INTO password_reset_token
                (recovery_email_id, token_hash, requested_at, expires_at)
            SELECT id, ?, ?, ?
            FROM recovery_email
            WHERE email_lookup_hash = ? AND deleted_at IS NULL
            """;
    private static final String CONSUME_AND_RESET = """
            WITH consumed AS (
                UPDATE password_reset_token
                SET used_at = ?
                WHERE token_hash = ?
                  AND used_at IS NULL
                  AND invalidated_at IS NULL
                  AND ? < expires_at
                RETURNING recovery_email_id
            ), changed AS (
                UPDATE authentication_credential AS credential
                SET password_hash = ?, password_changed_at = ?, status = 'ACTIVE',
                    failed_attempts = 0, locked_until = NULL, updated_at = ?
                FROM recovery_email AS email, consumed
                WHERE email.id = consumed.recovery_email_id
                  AND email.deleted_at IS NULL
                  AND credential.user_id = email.user_id
                RETURNING credential.user_id
            ), revoked AS (
                UPDATE user_session AS session
                SET revoked_at = ?, revoke_reason = 'PASSWORD_RESET'
                FROM changed
                WHERE session.user_id = changed.user_id
                  AND session.revoked_at IS NULL
            )
            SELECT count(*) FROM changed
            """;

    private final JdbcTemplate jdbc;
    public PostgresPasswordResetStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public boolean create(byte[] emailLookupHash, byte[] tokenHash,
            OffsetDateTime requestedAt, OffsetDateTime expiresAt) {
        jdbc.update(INVALIDATE_ACTIVE, requestedAt, emailLookupHash.clone());
        return jdbc.update(CREATE, tokenHash.clone(), requestedAt, expiresAt,
                emailLookupHash.clone()) == 1;
    }

    @Override
    @Transactional
    public boolean consumeAndReset(byte[] tokenHash, String passwordHash, OffsetDateTime usedAt) {
        Integer changed = jdbc.queryForObject(CONSUME_AND_RESET, Integer.class,
                usedAt, tokenHash.clone(), usedAt, passwordHash,
                usedAt, usedAt, usedAt);
        return changed != null && changed == 1;
    }
}
