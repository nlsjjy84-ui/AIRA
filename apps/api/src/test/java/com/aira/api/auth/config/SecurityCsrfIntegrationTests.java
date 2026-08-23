package com.aira.api.auth.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:csrf-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
class SecurityCsrfIntegrationTests {
    @Autowired WebApplicationContext context;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void unsafeAuthEndpointsRejectMissingCsrfToken() throws Exception {
        for (String path : new String[] {
                "/api/auth/signup", "/api/auth/login", "/api/auth/logout",
                "/api/auth/recovery-email/verifications",
                "/api/auth/recovery-email/verifications/confirm",
                "/api/auth/password-reset/requests",
                "/api/auth/password-reset/confirm"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void validCsrfTokenPassesProtectionForPublicAuthEndpoints() throws Exception {
        for (String path : new String[] {
                "/api/auth/signup", "/api/auth/login",
                "/api/auth/recovery-email/verifications/confirm",
                "/api/auth/password-reset/requests",
                "/api/auth/password-reset/confirm"}) {
            mvc.perform(post(path).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/auth/logout").with(csrf()))
                .andExpect(status().isNoContent());
    }

    @Test
    void spaPublishesReadableCsrfCookieAndProtectsEveryUnsafeMethod() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));

        mvc.perform(put("/api/example")).andExpect(status().isForbidden());
        mvc.perform(patch("/api/example")).andExpect(status().isForbidden());
        mvc.perform(delete("/api/example")).andExpect(status().isForbidden());
    }

    @Test
    void userInterestEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 401
                                || result.getResponse().getStatus() == 403));
        mvc.perform(get("/api/me/interests"))
                .andExpect(result -> assertTrue(
                        result.getResponse().getStatus() == 401
                                || result.getResponse().getStatus() == 403));
    }

    @Test
    void currentUserReturnsMinimalPrincipalForAuthenticatedRequest() throws Exception {
        var principal = new com.aira.api.auth.security.AiraPrincipal(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000010"), "AiraUser");
        var authentication = org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken.authenticated(
                        principal, null, java.util.List.of());
        mvc.perform(get("/api/me").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.nickname").value("AiraUser"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void companyFinancialFactsGetIsExplicitlyPublicForAnonymousAndAuthenticatedClients()
            throws Exception {
        String path = "/api/companies/00000000-0000-0000-0000-000000000001/financial-facts";
        mvc.perform(get(path).param("periodStart", "2025-01-01")
                        .param("periodEnd", "2025-12-31"))
                .andExpect(status().isNotFound());
        mvc.perform(get(path).with(user("authenticated-user"))
                        .param("periodStart", "2025-01-01")
                        .param("periodEnd", "2025-12-31"))
                .andExpect(status().isNotFound());
        mvc.perform(post(path)).andExpect(status().isForbidden());
    }

    @Test
    void companyDiscoveryAndPeriodsAreExplicitlyPublicGetOnly() throws Exception {
        mvc.perform(get("/api/companies")).andExpect(status().isOk());
        mvc.perform(get("/api/companies").with(user("authenticated-user")))
                .andExpect(status().isOk());

        String periods = "/api/companies/00000000-0000-0000-0000-000000000001/financial-periods";
        mvc.perform(get(periods)).andExpect(status().isNotFound());
        mvc.perform(get(periods).with(user("authenticated-user")))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/companies")).andExpect(status().isForbidden());
        mvc.perform(post(periods)).andExpect(status().isForbidden());
    }

    @Test
    void companyEventsArePublicReadOnlyForAnonymousAndAuthenticatedClients() throws Exception {
        String events = "/api/companies/00000000-0000-0000-0000-000000000001/events";
        mvc.perform(get(events)).andExpect(status().isOk());
        mvc.perform(get(events).with(user("authenticated-user"))).andExpect(status().isOk());
        mvc.perform(post(events)).andExpect(status().isForbidden());
    }

    @Test
    void briefingEndpointsRequireSessionOwnershipAndCsrf() throws Exception {
        String current = "/api/me/briefings/current";
        mvc.perform(post(current)).andExpect(status().isForbidden());
        mvc.perform(get("/api/me/briefings/00000000-0000-0000-0000-000000000001"))
                .andExpect(result -> assertTrue(result.getResponse().getStatus() == 401
                        || result.getResponse().getStatus() == 403));

        var principal = new com.aira.api.auth.security.AiraPrincipal(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000010"), "AiraUser");
        var authentication = org.springframework.security.authentication
                .UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of());
        mvc.perform(post(current).with(csrf()).with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.authentication(authentication)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.status").value("EMPTY"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void alertEndpointsArePrivateAndReconciliationRequiresCsrf() throws Exception {
        mvc.perform(get("/api/me/alerts")).andExpect(result -> assertTrue(
                result.getResponse().getStatus() == 401 || result.getResponse().getStatus() == 403));
        mvc.perform(post("/api/me/alerts/reconcile")).andExpect(status().isForbidden());

        var principal = new com.aira.api.auth.security.AiraPrincipal(
                java.util.UUID.fromString("00000000-0000-0000-0000-000000000010"), "AiraUser");
        var auth = org.springframework.security.authentication.UsernamePasswordAuthenticationToken
                .authenticated(principal, null, java.util.List.of());
        mvc.perform(post("/api/me/alerts/reconcile").with(csrf()).with(
                        org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.alerts").isEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.passwordHash").doesNotExist());
    }
}
