package com.aira.api.auth.security;

import com.aira.api.auth.config.AuthProperties;
import java.security.SecureRandom;
import java.util.Base64;

public final class SessionTokenGenerator {
    private final SecureRandom secureRandom;
    private final int tokenBytes;

    public SessionTokenGenerator() {
        this(new SecureRandom(), AuthProperties.SESSION_TOKEN_BYTES);
    }

    SessionTokenGenerator(SecureRandom secureRandom, int tokenBytes) {
        if (tokenBytes < AuthProperties.SESSION_TOKEN_BYTES) {
            throw new IllegalArgumentException("Session token must contain at least 256 bits");
        }
        this.secureRandom = secureRandom;
        this.tokenBytes = tokenBytes;
    }

    public String generate() {
        byte[] randomBytes = new byte[tokenBytes];
        secureRandom.nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }
}
