package com.aira.api.auth.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.aira.api.auth.service.SessionAuthenticationService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionAuthenticationFilterTests {
    SessionAuthenticationService service = mock(SessionAuthenticationService.class);
    SessionAuthenticationFilter filter = new SessionAuthenticationFilter(service);

    @BeforeEach
    void initializeContext() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void missingCookieContinuesWithoutAuthentication() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/example");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        verifyNoInteractions(service);
        verify(chain).doFilter(request, response);
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validCookieSetsMinimalAuthenticatedPrincipal() throws Exception {
        UUID userId = UUID.randomUUID();
        when(service.authenticate("opaque-token"))
                .thenReturn(Optional.of(new AiraPrincipal(userId, "AiraUser")));
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/example");
        request.setCookies(new Cookie(SessionCookieFactory.COOKIE_NAME, "opaque-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertTrue(authentication.isAuthenticated());
        assertEquals(new AiraPrincipal(userId, "AiraUser"), authentication.getPrincipal());
        assertNull(authentication.getCredentials());
        assertTrue(authentication.getAuthorities().isEmpty());
        verify(chain).doFilter(request, response);
    }

    @Test
    void invalidCookieContinuesUnauthenticated() throws Exception {
        when(service.authenticate("invalid-token")).thenReturn(Optional.empty());
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/example");
        request.setCookies(new Cookie(SessionCookieFactory.COOKIE_NAME, "invalid-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, chain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(chain).doFilter(request, response);
    }

    @Test
    void signupAndLoginIgnoreStaleSessionCookie() throws Exception {
        for (String path : new String[] {"/api/auth/signup", "/api/auth/login"}) {
            FilterChain chain = mock(FilterChain.class);
            MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
            request.setCookies(new Cookie(SessionCookieFactory.COOKIE_NAME, "stale-token"));
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, chain);
            verify(chain).doFilter(request, response);
        }
        verifyNoInteractions(service);
    }
}
