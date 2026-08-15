package com.aira.api.auth.security;

import com.aira.api.auth.config.AuthProperties;
import org.springframework.http.ResponseCookie;

public final class SessionCookieFactory {
    public static final String COOKIE_NAME = "AIRA_SESSION";
    private final boolean secure;
    private final String sameSite;

    public SessionCookieFactory(AuthProperties.Session properties) {
        this.secure = properties.isCookieSecure();
        this.sameSite = properties.getCookieSameSite();
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalArgumentException("Session cookie SameSite must be configured");
        }
    }

    public ResponseCookie create(String rawToken) {
        return ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true)
                .secure(secure)
                .sameSite(sameSite)
                .path("/")
                .build();
    }
}
