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
        assertEquals(1, count("SELECT count(*) FROM recovery_email WHERE deleted_at IS NULL"));

        sender.resetToken = null;
        passwordReset.request("unknown-" + email);
        assertEquals(null, sender.resetToken);
        passwordReset.request(email);
        String resetToken = sender.resetToken;

        passwordReset.confirm(resetToken, newPassword);
        assertThrows(InvalidPasswordResetTokenException.class,
                () -> passwordReset.confirm(resetToken, newPassword));
        assertThrows(AuthenticationFailedException.class,
                () -> login.login(new LoginRequest(nickname, oldPassword)));
        assertEquals(2, count("SELECT count(*) FROM user_session WHERE revoke_reason = 'PASSWORD_RESET'"));

        login.login(new LoginRequest(nickname, newPassword));
        assertEquals(1, count("SELECT count(*) FROM user_session WHERE revoked_at IS NULL"));
    }

    private UUID userId(String nickname) {
        return jdbc.queryForObject("SELECT id FROM app_user WHERE nickname = ?", UUID.class, nickname);
    }

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
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
