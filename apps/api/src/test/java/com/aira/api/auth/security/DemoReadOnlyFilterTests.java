package com.aira.api.auth.security;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.auth.service.DemoLoginService;
import jakarta.servlet.FilterChain;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DemoReadOnlyFilterTests {
    @AfterEach
    void clear() { SecurityContextHolder.clearContext(); }

    private static void signIn(String nickname) {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                new AiraPrincipal(UUID.randomUUID(), nickname), null, List.of()));
    }

    private static boolean passes(String method, String path) throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        FilterChain chain = (req, res) -> called.set(true);
        MockHttpServletResponse response = new MockHttpServletResponse();
        new DemoReadOnlyFilter().doFilter(new MockHttpServletRequest(method, path), response, chain);
        if (!called.get()) {
            assertEquals(403, response.getStatus());
            assertTrue(response.getContentAsString().contains("DEMO_READ_ONLY"));
        }
        return called.get();
    }

    @Test
    void demoCanReadAndUseAiReadingHelpButCannotWrite() throws Exception {
        signIn(DemoLoginService.DEMO_NICKNAME);
        assertTrue(passes("GET", "/api/me/interests"));
        assertTrue(passes("POST", "/api/me/assist/summary"));
        assertTrue(passes("POST", "/api/me/assist/claims"));
        assertTrue(passes("POST", "/api/auth/logout"));
        assertTrue(passes("POST", "/api/me/briefings/current"));
        assertTrue(passes("POST", "/api/me/alerts/reconcile"));
        // 결제 시험은 해 볼 수 있다(승인 직후 전액 취소, 플러스는 켜지지 않음).
        assertTrue(passes("POST", "/api/me/payments/orders"));
        assertTrue(passes("POST", "/api/me/payments/confirm"));
        assertFalse(passes("POST", "/api/me/finance/demo-import"));
        assertFalse(passes("POST", "/api/me/interests/" + UUID.randomUUID()));
        assertFalse(passes("DELETE", "/api/me/interests/" + UUID.randomUUID()));
        assertFalse(passes("POST", "/api/auth/recovery-email/verifications"));
        assertFalse(passes("PUT", "/api/me/finance/accounts"));
    }

    @Test
    void normalUsersAreNotAffected() throws Exception {
        signIn("AiraUser");
        assertTrue(passes("POST", "/api/me/interests/" + UUID.randomUUID()));
        assertTrue(passes("DELETE", "/api/me/interests/" + UUID.randomUUID()));
    }

    @Test
    void anonymousRequestsPassThroughToNormalSecurity() throws Exception {
        assertTrue(passes("POST", "/api/me/interests/" + UUID.randomUUID()));
    }
}
