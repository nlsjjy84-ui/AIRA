package com.aira.api.personalfinance;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.auth.security.PasswordHasher;
import com.aira.api.auth.security.SessionTokenHasher;
import com.aira.api.personalfinance.domain.FinanceConnectionSource;
import com.aira.api.personalfinance.dto.FinanceConsentRequest;
import com.aira.api.personalfinance.exception.FinanceConsentAlreadyActiveException;
import com.aira.api.personalfinance.exception.FinanceConsentNotFoundException;
import com.aira.api.personalfinance.exception.FinanceReauthenticationFailedException;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.FinanceConsentService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class PersonalFinanceSecurityPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordHasher passwords;
    @Autowired SessionTokenHasher tokenHasher;
    @Autowired FinanceAccessGrantService grants;
    @Autowired FinanceConsentService consents;

    @Test
    void financeGrantRequiresPasswordAndExactActiveSession() {
        Fixture owner = createFixture("FinanceOwner");
        Fixture other = createFixture("FinanceOther");
        try {
            assertThrows(FinanceReauthenticationFailedException.class,
                    () -> grants.issue(owner.userId(), owner.rawSession(), "wrong-password"));

            String first = grants.issue(owner.userId(), owner.rawSession(), owner.rawPassword());
            assertTrue(grants.hasAccess(owner.userId(), owner.rawSession(), first));
            assertFalse(grants.hasAccess(other.userId(), other.rawSession(), first));
            assertFalse(grants.hasAccess(owner.userId(), "wrong-session", first));

            String second = grants.issue(owner.userId(), owner.rawSession(), owner.rawPassword());
            assertFalse(grants.hasAccess(owner.userId(), owner.rawSession(), first));
            assertTrue(grants.hasAccess(owner.userId(), owner.rawSession(), second));
            jdbc.update("""
                    UPDATE user_session SET revoked_at=CURRENT_TIMESTAMP, revoke_reason='LOGOUT'
                    WHERE id=?
                    """, owner.sessionId());
            assertFalse(grants.hasAccess(owner.userId(), owner.rawSession(), second));
        } finally {
            deleteFixture(owner);
            deleteFixture(other);
        }
    }


    @Test
    void repeatedFinanceReauthenticationFailuresArePersistentlyLocked() {
        Fixture owner = createFixture("FinanceLock");
        try {
            for (int attempt = 0; attempt < 5; attempt++) {
                assertThrows(FinanceReauthenticationFailedException.class,
                        () -> grants.issue(owner.userId(), owner.rawSession(), "wrong-password"));
            }
            assertEquals(5, jdbc.queryForObject(
                    "SELECT failed_attempts FROM personal_finance_reauth_guard WHERE user_id=?",
                    Integer.class, owner.userId()));
            assertNotNull(jdbc.queryForObject(
                    "SELECT locked_until FROM personal_finance_reauth_guard WHERE user_id=?",
                    OffsetDateTime.class, owner.userId()));
            assertThrows(FinanceReauthenticationFailedException.class,
                    () -> grants.issue(owner.userId(), owner.rawSession(), owner.rawPassword()));

            jdbc.update("UPDATE personal_finance_reauth_guard SET locked_until=CURRENT_TIMESTAMP-interval '1 second' WHERE user_id=?", owner.userId());
            String grant = grants.issue(owner.userId(), owner.rawSession(), owner.rawPassword());
            assertTrue(grants.hasAccess(owner.userId(), owner.rawSession(), grant));
            assertEquals(0, jdbc.queryForObject(
                    "SELECT failed_attempts FROM personal_finance_reauth_guard WHERE user_id=?",
                    Integer.class, owner.userId()));
        } finally {
            deleteFixture(owner);
        }
    }

    @Test
    void consentHistoryIsOwnedAppendOnlyAndRevocationDisconnectsConnection() {
        Fixture owner = createFixture("ConsentOwner");
        Fixture other = createFixture("ConsentOther");
        var request = new FinanceConsentRequest(
                FinanceConnectionSource.DEMO_IMPORT, "demo-bank", "v1", true, true);
        try {
            var consent = consents.create(owner.userId(), request);
            assertEquals(1, consents.findAll(owner.userId()).size());
            assertThrows(FinanceConsentAlreadyActiveException.class,
                    () -> consents.create(owner.userId(), request));
            assertThrows(FinanceConsentNotFoundException.class,
                    () -> consents.findOwned(other.userId(), consent.id()));
            assertThrows(FinanceConsentNotFoundException.class,
                    () -> consents.revoke(other.userId(), consent.id()));
            UUID connectionId = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO personal_finance_connection(
                        id,user_id,source_type,provider_key,display_name,status,
                        consented_at,created_at,updated_at,consent_id)
                    VALUES (?,?,'DEMO_IMPORT','demo-bank','Demo Bank','ACTIVE',
                            CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)
                    """, connectionId, owner.userId(), consent.id());

            consents.revoke(owner.userId(), consent.id());
            var revoked = consents.findOwned(owner.userId(), consent.id());
            assertNotNull(revoked.revokedAt());
            assertEquals("DISCONNECTED", jdbc.queryForObject(
                    "SELECT status FROM personal_finance_connection WHERE id=?",
                    String.class, connectionId));
            assertNotNull(jdbc.queryForObject(
                    "SELECT disconnected_at FROM personal_finance_connection WHERE id=?",
                    OffsetDateTime.class, connectionId));

            var replacement = consents.create(owner.userId(), request);
            assertNotEquals(consent.id(), replacement.id());
            assertEquals(2, consents.findAll(owner.userId()).size());
        } finally {
            deleteFixture(owner);
            deleteFixture(other);
        }
    }
    @Test
    void databaseRejectsCrossUserConsentLinkage() {
        Fixture owner = createFixture("CrossOwner");
        Fixture other = createFixture("CrossOther");
        try {
            var consent = consents.create(owner.userId(), new FinanceConsentRequest(
                    FinanceConnectionSource.DEMO_IMPORT, "cross-bank", "v1", true, true));
            assertThrows(DataAccessException.class, () -> jdbc.update("""
                    INSERT INTO personal_finance_connection(
                        id,user_id,source_type,provider_key,display_name,status,
                        consented_at,created_at,updated_at,consent_id)
                    VALUES (?,?,'DEMO_IMPORT','cross-bank','Cross Bank','ACTIVE',
                            CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)
                    """, UUID.randomUUID(), other.userId(), consent.id()));
        } finally {
            deleteFixture(owner);
            deleteFixture(other);
        }
    }

    private Fixture createFixture(String prefix) {
        UUID userId = UUID.randomUUID();
        UUID credentialId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String suffix = userId.toString().replace("-", "").substring(0, 8);
        String nickname = prefix + suffix;
        String credentialProof = "valid-test-proof-value";
        String sessionProof = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now().withNano(0);
        OffsetDateTime issued = now.minusMinutes(1);
        OffsetDateTime absolute = issued.plusHours(12);

        jdbc.update("""
                INSERT INTO app_user(id,nickname,nickname_normalized,status)
                VALUES (?,?,?,'ACTIVE')
                """, userId, nickname, nickname.toLowerCase());
        jdbc.update("""
                INSERT INTO authentication_credential(
                    id,user_id,password_hash,password_changed_at,status)
                VALUES (?,?,?,?,'ACTIVE')
                """, credentialId, userId, passwords.hash(credentialProof), now);
        jdbc.update("""
                INSERT INTO user_session(
                    id,user_id,credential_id,token_hash,issued_at,last_seen_at,
                    idle_expires_at,absolute_expires_at)
                VALUES (?,?,?,?,?,?,?,?)
                """, sessionId, userId, credentialId, tokenHasher.hash(sessionProof),
                issued, now, now.plusMinutes(30), absolute);
        return new Fixture(userId, sessionId, sessionProof, credentialProof);
    }

    private void deleteFixture(Fixture fixture) {
        jdbc.update("DELETE FROM app_user WHERE id=?", fixture.userId());
    }

    private record Fixture(UUID userId, UUID sessionId, String rawSession, String rawPassword) {}
}
