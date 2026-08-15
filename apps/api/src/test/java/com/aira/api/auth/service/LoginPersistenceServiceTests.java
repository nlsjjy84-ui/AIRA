package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.user.domain.AppUser;
import com.aira.api.user.domain.AuthenticationCredential;
import com.aira.api.user.domain.UserSession;
import com.aira.api.user.repository.AppUserRepository;
import com.aira.api.user.repository.AuthenticationCredentialRepository;
import com.aira.api.user.repository.UserSessionRepository;
import com.aira.api.auth.exception.AuthenticationFailedException;
import jakarta.persistence.EntityManager;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Import(LoginPersistenceService.class)
class LoginPersistenceServiceTests {
    @Autowired LoginPersistenceService persistence;
    @Autowired AppUserRepository users;
    @Autowired AuthenticationCredentialRepository credentials;
    @Autowired UserSessionRepository sessions;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    @Test
    void storesOnlyTokenHashWithV3ExpiryPolicyAndAllowsMultipleSessions() {
        OffsetDateTime now = OffsetDateTime.now();
        AppUser user = users.save(AppUser.create("AiraUser", "airauser", now));
        AuthenticationCredential credential = credentials.save(
                AuthenticationCredential.create(user, "$argon2id$stored", now));
        byte[] firstHash = {1, 2, 3};
        byte[] secondHash = {4, 5, 6};

        UserSession first = persistence.save(user, credential, firstHash);
        UserSession second = persistence.save(user, credential, secondHash);

        assertEquals(2, sessions.count());
        assertArrayEquals(firstHash, first.getTokenHash());
        assertArrayEquals(secondHash, second.getTokenHash());
        assertEquals(Duration.ofMinutes(30), Duration.between(first.getIssuedAt(), first.getIdleExpiresAt()));
        assertEquals(Duration.ofHours(12), Duration.between(first.getIssuedAt(), first.getAbsoluteExpiresAt()));
        assertEquals(first.getIssuedAt(), first.getLastSeenAt());
        assertNull(first.getRotatedFromSession());
        assertNull(first.getRevokedAt());
        assertNull(first.getRevokeReason());
    }

    @Test
    void revokedCredentialAfterPasswordVerificationCreatesNoSession() {
        Account account = account("RevokedUser", "revokeduser");
        jdbc.update("update authentication_credential set status = 'REVOKED' where id = ?",
                account.credential().getId());
        entityManager.clear();

        assertThrows(AuthenticationFailedException.class,
                () -> persistence.save(account.user(), account.credential(), new byte[32]));
        assertEquals(0, sessions.count());
    }

    @Test
    void lockedUserAfterPasswordVerificationCreatesNoSession() {
        Account account = account("LockedUser", "lockeduser");
        jdbc.update("update app_user set status = 'LOCKED' where id = ?", account.user().getId());
        entityManager.clear();

        assertThrows(AuthenticationFailedException.class,
                () -> persistence.save(account.user(), account.credential(), new byte[32]));
        assertEquals(0, sessions.count());
    }

    @Test
    void deletedUserAfterPasswordVerificationCreatesNoSession() {
        Account account = account("DeletedUser", "deleteduser");
        jdbc.update("update app_user set status = 'DELETED', deleted_at = current_timestamp where id = ?",
                account.user().getId());
        entityManager.clear();

        assertThrows(AuthenticationFailedException.class,
                () -> persistence.save(account.user(), account.credential(), new byte[32]));
        assertEquals(0, sessions.count());
    }

    private Account account(String nickname, String normalizedNickname) {
        OffsetDateTime now = OffsetDateTime.now();
        AppUser user = users.saveAndFlush(AppUser.create(nickname, normalizedNickname, now));
        AuthenticationCredential credential = credentials.saveAndFlush(
                AuthenticationCredential.create(user, "$argon2id$stored", now));
        return new Account(user, credential);
    }

    private record Account(AppUser user, AuthenticationCredential credential) {}
}
