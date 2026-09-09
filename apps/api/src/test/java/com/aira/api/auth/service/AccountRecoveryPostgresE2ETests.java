package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aira.api.auth.dto.LoginRequest;
import com.aira.api.auth.dto.SignupRequest;
import com.aira.api.auth.email.EmailSender;
import com.aira.api.auth.exception.AuthenticationFailedException;
import com.aira.api.auth.exception.InvalidPasswordResetTokenException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class AccountRecoveryPostgresE2ETests {
    @Autowired AuthService signup;
    @Autowired LoginService login;
    @Autowired RecoveryEmailVerificationService recoveryEmail;
    @Autowired PasswordResetService passwordReset;
    @Autowired CapturingEmailSender sender;
    @Autowired JdbcTemplate jdbc;
    @Autowired LoginPersistenceService persistence;
    @Autowired com.aira.api.user.repository.AppUserRepository users;
    @Autowired com.aira.api.user.repository.AuthenticationCredentialRepository credentials;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test
    void verifiesRecoveryEmailResetsPasswordRevokesSessionsAndAllowsNewLogin() {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String nickname = "Recovery" + suffix;
        String email = "recovery-" + suffix + "@example.test";
        String oldPassword = "original-secure-password";
        String newPassword = "changed-secure-password";

        signup.signup(new SignupRequest(nickname, oldPassword));
        login.login(new LoginRequest(nickname, oldPassword));
        login.login(new LoginRequest(nickname, oldPassword));

        recoveryEmail.request(userId(nickname), email);
        recoveryEmail.confirm(sender.verificationToken);
        // Replacing an already verified address must retire the old row before insertion.
        recoveryEmail.request(userId(nickname), email);
        org.junit.jupiter.api.Assertions.assertTrue(recoveryEmail.confirm(sender.verificationToken));
        UUID userId = userId(nickname);
        assertEquals(1, count("SELECT count(*) FROM recovery_email WHERE user_id = ? AND deleted_at IS NULL",
                userId));

        sender.resetToken = null;
        passwordReset.request("unknown-" + email);
        assertEquals(null, sender.resetToken);
        passwordReset.request(email);
        String resetToken = sender.resetToken;
        var verifiedUser = users.findById(userId).orElseThrow();
        var verifiedCredential = credentials.findByUserId(userId).orElseThrow();

        passwordReset.confirm(resetToken, newPassword);
        assertThrows(AuthenticationFailedException.class,
                () -> persistence.save(verifiedUser, verifiedCredential, new byte[32]));
        assertThrows(InvalidPasswordResetTokenException.class,
                () -> passwordReset.confirm(resetToken, newPassword));
        assertThrows(AuthenticationFailedException.class,
                () -> login.login(new LoginRequest(nickname, oldPassword)));
        assertEquals(2, count("SELECT count(*) FROM user_session WHERE user_id = ? AND revoke_reason = 'PASSWORD_RESET'",
                userId));

        login.login(new LoginRequest(nickname, newPassword));
        assertEquals(1, count("SELECT count(*) FROM user_session WHERE user_id = ? AND revoked_at IS NULL",
                userId));
    }

    @Test
    void resetWaitsForInFlightSessionIssuanceAndRevokesThatSession() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String nickname = "ResetRace" + suffix;
        String password = "original-secure-password";
        signup.signup(new SignupRequest(nickname, password));
        UUID id = userId(nickname);
        try {
            String email = "race-" + suffix + "@example.test";
            recoveryEmail.request(id, email);
            recoveryEmail.confirm(sender.verificationToken);
            passwordReset.request(email);
            String token = sender.resetToken;
            var user = users.findById(id).orElseThrow();
            var credential = credentials.findByUserId(id).orElseThrow();
            var sessionReady = new java.util.concurrent.CountDownLatch(1);
            var releaseSession = new java.util.concurrent.CountDownLatch(1);
            var resetStarted = new java.util.concurrent.CountDownLatch(1);
            try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var issuing = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions)
                        .execute(status -> {
                            persistence.save(user, credential, new byte[32]);
                            sessionReady.countDown();
                            try {
                                if (!releaseSession.await(10, java.util.concurrent.TimeUnit.SECONDS)) throw new AssertionError("Session release timeout");
                            } catch (InterruptedException error) { throw new RuntimeException(error); }
                            return null;
                        }));
                org.junit.jupiter.api.Assertions.assertTrue(sessionReady.await(10, java.util.concurrent.TimeUnit.SECONDS));
                var resetting = executor.submit(() -> { resetStarted.countDown(); passwordReset.confirm(token, "changed-secure-password"); });
                resetStarted.await(10, java.util.concurrent.TimeUnit.SECONDS);
                try {
                    assertThrows(java.util.concurrent.TimeoutException.class,
                            () -> resetting.get(200, java.util.concurrent.TimeUnit.MILLISECONDS));
                } finally { releaseSession.countDown(); }
                issuing.get(10, java.util.concurrent.TimeUnit.SECONDS);
                resetting.get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
            assertEquals(0, count("SELECT count(*) FROM user_session WHERE user_id=? AND revoked_at IS NULL", id));
            assertEquals(1, count("SELECT count(*) FROM user_session WHERE user_id=? AND revoke_reason='PASSWORD_RESET'", id));
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id=?", id);
        }
    }

    private UUID userId(String nickname) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE nickname = ?", UUID.class, nickname);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    @TestConfiguration
    static class Configuration {
        @Bean
        @Primary
        CapturingEmailSender capturingEmailSender() {
            return new CapturingEmailSender();
        }
    }

    static final class CapturingEmailSender implements EmailSender {
        String verificationToken;
        String resetToken;

        @Override
        public void sendRecoveryEmailVerification(String email, String rawToken) {
            verificationToken = rawToken;
        }

        @Override
        public void sendPasswordReset(String email, String rawToken) {
            resetToken = rawToken;
        }
    }
}
