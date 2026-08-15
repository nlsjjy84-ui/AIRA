package com.aira.api.auth.security;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

class SessionCookieFactoryTests {
    @Test
    void productionDefaultIsHttpOnlySecureAndSameSiteLax() {
        ResponseCookie cookie = new SessionCookieFactory(new AuthProperties().getSession()).create("token");
        assertTrue(cookie.isHttpOnly());
        assertTrue(cookie.isSecure());
        assertEquals("Lax", cookie.getSameSite());
        assertEquals("/", cookie.getPath());
    }

    @Test
    void localDevelopmentCanExplicitlyDisableSecure() {
        AuthProperties properties = new AuthProperties();
        properties.getSession().setCookieSecure(false);
        ResponseCookie cookie = new SessionCookieFactory(properties.getSession()).create("token");
        assertFalse(cookie.isSecure());
        assertTrue(cookie.isHttpOnly());
        assertEquals("Lax", cookie.getSameSite());
    }
}
