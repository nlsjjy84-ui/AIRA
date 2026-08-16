package com.aira.api.auth.config;

import com.aira.api.auth.security.NicknameNormalizer;
import com.aira.api.auth.security.PasswordHasher;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.auth.security.SessionTokenGenerator;
import com.aira.api.auth.security.SessionTokenHasher;
import com.aira.api.auth.security.RecoveryEmailProtector;
import com.aira.api.auth.security.VerificationTokenGenerator;
import com.aira.api.auth.email.EmailSender;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class AuthConfiguration {
    @Bean
    NicknameNormalizer nicknameNormalizer(AuthProperties properties) {
        return new NicknameNormalizer(properties);
    }

    @Bean
    PasswordHasher passwordHasher(AuthProperties properties) {
        return new PasswordHasher(properties.getArgon2());
    }

    @Bean
    SessionTokenGenerator sessionTokenGenerator() {
        return new SessionTokenGenerator();
    }

    @Bean
    SessionTokenHasher sessionTokenHasher() {
        return new SessionTokenHasher();
    }

    @Bean
    SessionCookieFactory sessionCookieFactory(AuthProperties properties) {
        return new SessionCookieFactory(properties.getSession());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    RecoveryEmailProtector recoveryEmailProtector(AuthProperties properties) {
        var recovery = properties.getRecoveryEmail();
        return new RecoveryEmailProtector(
                recovery.getEncryptionKey(), recovery.getLookupKey(), recovery.getKeyVersion());
    }

    @Bean
    VerificationTokenGenerator verificationTokenGenerator(SessionTokenGenerator generator) {
        return generator::generate;
    }

    @Bean
    @ConditionalOnMissingBean(EmailSender.class)
    EmailSender emailSender() {
        return (email, rawToken) -> {
            throw new IllegalStateException("Recovery email sender is not configured");
        };
    }
}
