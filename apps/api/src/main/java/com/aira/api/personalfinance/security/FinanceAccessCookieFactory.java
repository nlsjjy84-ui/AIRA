package com.aira.api.personalfinance.security;

import com.aira.api.auth.config.AuthProperties;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/** HTTP-only cookie for the short-lived finance step-up grant. */
@Component
public final class FinanceAccessCookieFactory {
    public static final String COOKIE_NAME = "AIRA_FINANCE_ACCESS";
    private static final Duration MAX_AGE = Duration.ofMinutes(10);
    private final boolean secure;
    private final String sameSite;

    public FinanceAccessCookieFactory(AuthProperties properties) {
        this.secure = properties.getSession().isCookieSecure();
        this.sameSite = properties.getSession().getCookieSameSite();
        if (sameSite == null || sameSite.isBlank()) {
            throw new IllegalArgumentException("Finance access cookie SameSite must be configured");
        }
    }

    public ResponseCookie create(String rawToken) {
        return ResponseCookie.from(COOKIE_NAME, rawToken)
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path("/api/me/finance").maxAge(MAX_AGE).build();
    }

    public ResponseCookie delete() {
        return ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path("/api/me/finance").maxAge(Duration.ZERO).build();
    }
}
