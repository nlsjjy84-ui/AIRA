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
                UPDATE recovery_email_verification
                SET verified_at = ?
                WHERE token_hash = ?
                  AND verified_at IS NULL
                  AND invalidated_at IS NULL
                  AND ? < expires_at
                RETURNING user_id, candidate_email_ciphertext,
                          candidate_email_lookup_hash, encryption_key_version
            """;

    private final JdbcTemplate jdbc;

    public PostgresRecoveryEmailVerificationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    @Transactional
    public void create(UUID userId, byte[] ciphertext, byte[] lookupHash, short keyVersion,
            byte[] tokenHash, OffsetDateTime requestedAt, OffsetDateTime expiresAt) {
        jdbc.queryForObject("SELECT id FROM app_user WHERE id=? FOR UPDATE", UUID.class, userId);
        jdbc.update(INVALIDATE_PENDING, requestedAt, userId);
        jdbc.update(INSERT_VERIFICATION, userId, ciphertext.clone(), lookupHash.clone(), keyVersion,
                tokenHash.clone(), requestedAt, expiresAt);
    }

    @Override
    @Transactional
    public boolean confirm(byte[] tokenHash, OffsetDateTime verifiedAt) {
        jdbc.query("""
                SELECT u.id FROM app_user u JOIN recovery_email_verification v ON v.user_id=u.id
                WHERE v.token_hash=? FOR UPDATE OF u
                """, (rs, row) -> rs.getObject(1), tokenHash.clone());
        verifiedAt = jdbc.queryForObject("SELECT clock_timestamp()", OffsetDateTime.class);
        var verified = jdbc.query(CONFIRM, (rs, row) -> new Verified(
                rs.getObject(1, UUID.class), rs.getBytes(2), rs.getBytes(3), rs.getShort(4)),
                verifiedAt, tokenHash.clone(), verifiedAt);
        if (verified.isEmpty()) return false;
        Verified value = verified.getFirst();
        // PostgreSQL data-modifying sibling CTEs do not guarantee retirement before insertion.
        jdbc.update("UPDATE recovery_email SET deleted_at=?,updated_at=? WHERE user_id=? AND deleted_at IS NULL",
                verifiedAt, verifiedAt, value.userId());
        jdbc.update("""
                INSERT INTO recovery_email(user_id,purpose,email_ciphertext,email_lookup_hash,
                    encryption_key_version,verified_at,created_at,updated_at)
                VALUES(?,'ACCOUNT_RECOVERY',?,?,?,?,?,?)
                """, value.userId(), value.ciphertext(), value.lookupHash(), value.keyVersion(),
                verifiedAt, verifiedAt, verifiedAt);
        return true;
    }

    private record Verified(UUID userId, byte[] ciphertext, byte[] lookupHash, short keyVersion) {}
}
