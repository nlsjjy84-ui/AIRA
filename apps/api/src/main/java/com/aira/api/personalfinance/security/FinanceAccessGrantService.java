package com.aira.api.personalfinance.security;

import com.aira.api.auth.security.PasswordHasher;
import com.aira.api.auth.security.SessionTokenGenerator;
import com.aira.api.auth.security.SessionTokenHasher;
import com.aira.api.personalfinance.exception.FinanceAccessRequiredException;
import com.aira.api.personalfinance.exception.FinanceReauthenticationFailedException;
import com.aira.api.user.domain.CredentialStatus;
import com.aira.api.user.repository.AuthenticationCredentialRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues a short-lived finance capability after password reauthentication.
 * The capability is bound to the current AIRA session, so logout/session expiry
 * invalidates finance access even if the finance cookie is still present.
 */
@Service
public class FinanceAccessGrantService {
    private static final Duration GRANT_TTL = Duration.ofMinutes(10);
    private static final int MAX_REAUTH_FAILURES = 5;
    private static final Duration REAUTH_LOCK = Duration.ofMinutes(15);
    private final JdbcTemplate jdbc;
    private final AuthenticationCredentialRepository credentials;
    private final PasswordHasher passwords;
    private final SessionTokenGenerator tokens;
    private final SessionTokenHasher tokenHasher;
    private final Clock clock;

    public FinanceAccessGrantService(JdbcTemplate jdbc, AuthenticationCredentialRepository credentials,
            PasswordHasher passwords, SessionTokenGenerator tokens,
            SessionTokenHasher tokenHasher, Clock clock) {
        this.jdbc = jdbc;
        this.credentials = credentials;
        this.passwords = passwords;
        this.tokens = tokens;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
    }

    @Transactional(noRollbackFor = FinanceReauthenticationFailedException.class)
    public String issue(UUID userId, String rawSessionToken, String rawPassword) {
        if (userId == null || rawPassword == null || rawPassword.isBlank()) {
            throw new FinanceReauthenticationFailedException();
        }
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID sessionId = findActiveSession(userId, rawSessionToken, now);
        var credential = credentials.findByUserId(userId).orElse(null);
        if (sessionId == null || credential == null
                || credential.getStatus() != CredentialStatus.ACTIVE) {
            throw new FinanceReauthenticationFailedException();
        }
        requireReauthAttemptAllowed(userId, now);
        if (!passwords.matches(rawPassword, credential.getPasswordHash())) {
            recordReauthFailure(userId, now);
            throw new FinanceReauthenticationFailedException();
        }
        clearReauthFailures(userId, now);

        String rawGrant = tokens.generate();
        byte[] grantHash = tokenHasher.hash(rawGrant);
        jdbc.update("""
                UPDATE personal_finance_access_grant
                SET revoked_at=?
                WHERE session_id=? AND revoked_at IS NULL
                """, now, sessionId);
        jdbc.update("""
                INSERT INTO personal_finance_access_grant(
                    id,user_id,session_id,token_hash,issued_at,expires_at)
                VALUES (?,?,?,?,?,?)
                """, UUID.randomUUID(), userId, sessionId, grantHash, now, now.plus(GRANT_TTL));
        return rawGrant;
    }

    @Transactional(readOnly = true)
    public void requireAccess(UUID userId, String rawSessionToken, String rawGrantToken) {
        if (!hasAccess(userId, rawSessionToken, rawGrantToken)) {
            throw new FinanceAccessRequiredException();
        }
    }

    @Transactional(readOnly = true)
    public boolean hasAccess(UUID userId, String rawSessionToken, String rawGrantToken) {
        if (userId == null || rawSessionToken == null || rawSessionToken.isBlank()
                || rawGrantToken == null || rawGrantToken.isBlank()) {
            return false;
        }
        byte[] sessionHash = tokenHasher.hash(rawSessionToken);
        byte[] grantHash = tokenHasher.hash(rawGrantToken);
        OffsetDateTime now = OffsetDateTime.now(clock);
        Boolean allowed = jdbc.queryForObject("""
                SELECT EXISTS (
                    SELECT 1
                    FROM personal_finance_access_grant grant_row
                    JOIN user_session session_row
                      ON session_row.id=grant_row.session_id
                     AND session_row.user_id=grant_row.user_id
                    JOIN app_user app_user ON app_user.id=grant_row.user_id
                    JOIN authentication_credential credential
                      ON credential.id=session_row.credential_id
                     AND credential.user_id=grant_row.user_id
                    WHERE grant_row.user_id=?
                      AND grant_row.token_hash=?
                      AND session_row.token_hash=?
                      AND grant_row.revoked_at IS NULL
                      AND ? < grant_row.expires_at
                      AND session_row.revoked_at IS NULL
                      AND ? < session_row.idle_expires_at
                      AND ? < session_row.absolute_expires_at
                      AND app_user.status='ACTIVE'
                      AND credential.status='ACTIVE'
                )
                """, Boolean.class, userId, grantHash, sessionHash, now, now, now);
        return Boolean.TRUE.equals(allowed);
    }

    @Transactional
    public void revoke(UUID userId, String rawSessionToken) {
        UUID sessionId = findActiveSession(userId, rawSessionToken, OffsetDateTime.now(clock));
        if (sessionId == null) return;
        jdbc.update("""
                UPDATE personal_finance_access_grant
                SET revoked_at=?
                WHERE session_id=? AND user_id=? AND revoked_at IS NULL
                """, OffsetDateTime.now(clock), sessionId, userId);
    }

    /** Locks the per-user finance reauthentication guard before checking the password. */
    private void requireReauthAttemptAllowed(UUID userId, OffsetDateTime now) {
        jdbc.update("""
                INSERT INTO personal_finance_reauth_guard(user_id,failed_attempts,updated_at)
                VALUES (?,0,?) ON CONFLICT (user_id) DO NOTHING
                """, userId, now);
        var guards = jdbc.query("""
                SELECT failed_attempts, locked_until
                FROM personal_finance_reauth_guard WHERE user_id=? FOR UPDATE
                """, (rs, row) -> new ReauthGuard(rs.getInt(1), rs.getObject(2, OffsetDateTime.class)), userId);
        ReauthGuard guard = guards.getFirst();
        if (guard.lockedUntil() != null && now.isBefore(guard.lockedUntil())) {
            throw new FinanceReauthenticationFailedException();
        }
        if (guard.lockedUntil() != null) {
            jdbc.update("UPDATE personal_finance_reauth_guard SET failed_attempts=0,locked_until=NULL,updated_at=? WHERE user_id=?", now, userId);
        }
    }

    private void recordReauthFailure(UUID userId, OffsetDateTime now) {
        jdbc.update("""
                UPDATE personal_finance_reauth_guard
                SET failed_attempts=failed_attempts+1,
                    locked_until=CASE WHEN failed_attempts+1>=? THEN ? ELSE locked_until END,
                    updated_at=? WHERE user_id=?
                """, MAX_REAUTH_FAILURES, now.plus(REAUTH_LOCK), now, userId);
    }

    private void clearReauthFailures(UUID userId, OffsetDateTime now) {
        jdbc.update("UPDATE personal_finance_reauth_guard SET failed_attempts=0,locked_until=NULL,updated_at=? WHERE user_id=?", now, userId);
    }

    private record ReauthGuard(int failedAttempts, OffsetDateTime lockedUntil) {}

    private UUID findActiveSession(UUID userId, String rawSessionToken, OffsetDateTime now) {
        if (userId == null || rawSessionToken == null || rawSessionToken.isBlank()) return null;
        byte[] sessionHash = tokenHasher.hash(rawSessionToken);
        var ids = jdbc.query("""
                SELECT session_row.id
                FROM user_session session_row
                JOIN app_user app_user ON app_user.id=session_row.user_id
                JOIN authentication_credential credential
                  ON credential.id=session_row.credential_id
                 AND credential.user_id=session_row.user_id
                WHERE session_row.user_id=?
                  AND session_row.token_hash=?
                  AND session_row.revoked_at IS NULL
                  AND ? < session_row.idle_expires_at
                  AND ? < session_row.absolute_expires_at
                  AND app_user.status='ACTIVE'
                  AND credential.status='ACTIVE'
                """, (rs, row) -> rs.getObject(1, UUID.class), userId, sessionHash, now, now);
        return ids.isEmpty() ? null : ids.getFirst();
    }
}
