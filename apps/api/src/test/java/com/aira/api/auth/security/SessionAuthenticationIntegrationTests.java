package com.aira.api.auth.security;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aira.api.auth.service.SessionAuthenticationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

class SessionAuthenticationIntegrationTests {
    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void filterAuthenticatesValidSessionWithoutAuthorizingEndpoints() throws Exception {
        SessionAuthenticationService service = mock(SessionAuthenticationService.class);
        when(service.authenticate("valid-token")).thenReturn(Optional.of(
                new AiraPrincipal(UUID.fromString("00000000-0000-0000-0000-000000000001"), "AiraUser")));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new SessionAuthenticationFilter(service)).build();

        mvc.perform(get("/probe").cookie(
                        new jakarta.servlet.http.Cookie(SessionCookieFactory.COOKIE_NAME, "valid-token")))
                .andExpect(status().isOk())
                .andExpect(content().string("AiraUser"));
        SecurityContextHolder.clearContext();
        mvc.perform(get("/probe"))
                .andExpect(status().isOk())
                .andExpect(content().string("anonymous"));
    }

    @Test
    void authEndpointsIgnoreInvalidCookie() throws Exception {
        SessionAuthenticationService service = mock(SessionAuthenticationService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ProbeController())
                .addFilters(new SessionAuthenticationFilter(service)).build();

        mvc.perform(post("/api/auth/login").cookie(
                        new jakarta.servlet.http.Cookie(SessionCookieFactory.COOKIE_NAME, "invalid-token")))
                .andExpect(status().isOk());
        verifyNoInteractions(service);
    }

    @RestController
    static class ProbeController {
        @GetMapping("/probe")
        String probe() {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return authentication == null ? "anonymous" : authentication.getName();
        }

        @PostMapping("/api/auth/login")
        void login() {}
    }
}
