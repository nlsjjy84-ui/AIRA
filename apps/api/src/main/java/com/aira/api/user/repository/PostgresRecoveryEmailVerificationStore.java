package com.aira.api.user.repository;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PostgresRecoveryEmailVerificationStore implements RecoveryEmailVerificationStore {
    private static final String INVALIDATE_PENDING = """
            UPDATE recovery_email_verification
            SET invalidated_at = ?
            WHERE user_id = ? AND verified_at IS NULL AND invalidated_at IS NULL
            """;
    private static final String INSERT_VERIFICATION = """
            INSERT INTO recovery_email_verification
                (user_id, candidate_email_ciphertext, candidate_email_lookup_hash,
                 encryption_key_version, token_hash, requested_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String CONFIRM = """
            WITH verified AS (
                UPDATE recovery_email_verification
                SET verified_at = ?
                WHERE token_hash = ?
                  AND verified_at IS NULL
                  AND invalidated_at IS NULL
                  AND ? < expires_at
                RETURNING user_id, candidate_email_ciphertext,
                          candidate_email_lookup_hash, encryption_key_version
            ), retired AS (
                UPDATE recovery_email AS email
                SET deleted_at = ?, updated_at = ?
                FROM verified
                WHERE email.user_id = verified.user_id AND email.deleted_at IS NULL
            )
            INSERT INTO recovery_email
                (user_id, purpose, email_ciphertext, email_lookup_hash,
                 encryption_key_version, verified_at, created_at, updated_at)
            SELECT user_id, 'ACCOUNT_RECOVERY', candidate_email_ciphertext,
                   candidate_email_lookup_hash, encryption_key_version, ?, ?, ?
            FROM verified
            """;

    private final JdbcTemplate jdbc;

    public PostgresRecoveryEmailVerificationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void create(UUID userId, byte[] ciphertext, byte[] lookupHash, short keyVersion,
            byte[] tokenHash, OffsetDateTime requestedAt, OffsetDateTime expiresAt) {
        jdbc.update(INVALIDATE_PENDING, requestedAt, userId);
        jdbc.update(INSERT_VERIFICATION, userId, ciphertext.clone(), lookupHash.clone(), keyVersion,
                tokenHash.clone(), requestedAt, expiresAt);
    }

    @Override
    @Transactional
    public boolean confirm(byte[] tokenHash, OffsetDateTime verifiedAt) {
        return jdbc.update(CONFIRM, verifiedAt, tokenHash.clone(), verifiedAt,
                verifiedAt, verifiedAt, verifiedAt, verifiedAt, verifiedAt) == 1;
    }
}
