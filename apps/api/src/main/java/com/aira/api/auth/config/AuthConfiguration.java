package com.aira.api.auth.config;

import com.aira.api.auth.security.NicknameNormalizer;
import com.aira.api.auth.security.PasswordHasher;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.auth.security.SessionTokenGenerator;
import com.aira.api.auth.security.SessionTokenHasher;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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
}
