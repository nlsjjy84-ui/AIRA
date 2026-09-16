package com.aira.api.personalfinance;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aira.api.auth.config.AuthProperties;
import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.personalfinance.controller.FinanceAccessController;
import com.aira.api.personalfinance.controller.FinanceConsentController;
import com.aira.api.personalfinance.controller.PersonalFinanceExceptionHandler;
import com.aira.api.personalfinance.controller.PersonalFinanceDataController;
import com.aira.api.personalfinance.exception.FinanceAccessRequiredException;
import com.aira.api.personalfinance.exception.FinanceConsentNotFoundException;
import com.aira.api.personalfinance.security.FinanceAccessCookieFactory;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.FinanceConsentService;
import com.aira.api.personalfinance.service.PersonalFinanceDataDeletionService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class PersonalFinanceSecurityControllerTests {
    FinanceAccessGrantService grants = mock(FinanceAccessGrantService.class);
    FinanceConsentService consents = mock(FinanceConsentService.class);
    PersonalFinanceDataDeletionService deletion = mock(PersonalFinanceDataDeletionService.class);
    UUID userId = UUID.randomUUID();
    MockMvc accessMvc;
    MockMvc consentMvc;
    MockMvc dataMvc;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver principal = new HandlerMethodArgumentResolver() {
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, WebDataBinderFactory factory) {
                return new AiraPrincipal(userId, "AiraUser");
            }
        };
        AuthProperties properties = new AuthProperties();
        properties.getSession().setCookieSecure(true);
        properties.getSession().setCookieSameSite("Lax");
        FinanceAccessCookieFactory cookies = new FinanceAccessCookieFactory(properties);

        accessMvc = MockMvcBuilders.standaloneSetup(new FinanceAccessController(grants, cookies))
                .setCustomArgumentResolvers(principal)
                .setControllerAdvice(new PersonalFinanceExceptionHandler())
                .build();
        consentMvc = MockMvcBuilders.standaloneSetup(new FinanceConsentController(grants, consents))
                .setCustomArgumentResolvers(principal)
                .setControllerAdvice(new PersonalFinanceExceptionHandler())
                .build();
        dataMvc = MockMvcBuilders.standaloneSetup(new PersonalFinanceDataController(grants, deletion, cookies))
                .setCustomArgumentResolvers(principal)
                .setControllerAdvice(new PersonalFinanceExceptionHandler())
                .build();
    }

    @Test
    void reauthenticationReturnsGrantOnlyAsHardenedCookie() throws Exception {
        when(grants.issue(eq(userId), eq("session-proof"), eq("credential-proof")))
                .thenReturn("grant-proof");

        accessMvc.perform(post("/api/me/finance/access/reauthenticate")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieFactory.COOKIE_NAME, "session-proof"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"credential-proof\"}"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("AIRA_FINANCE_ACCESS=grant-proof"),
                        containsString("Path=/api/me/finance"),
                        containsString("Max-Age=600"),
                        containsString("Secure"),
                        containsString("HttpOnly"),
                        containsString("SameSite=Lax"))));
        verify(grants).issue(userId, "session-proof", "credential-proof");
    }

    @Test
    void consentEndpointFailsClosedWithoutFinanceGrant() throws Exception {
        doThrow(new FinanceAccessRequiredException())
                .when(grants).requireAccess(eq(userId), any(), any());

        consentMvc.perform(get("/api/me/finance/consents")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieFactory.COOKIE_NAME, "session-proof")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(consents);
    }

    @Test
    void foreignConsentIdentifierDoesNotLeakOwnership() throws Exception {
        doNothing().when(grants).requireAccess(eq(userId), any(), any());
        UUID consentId = UUID.randomUUID();
        doThrow(new FinanceConsentNotFoundException())
                .when(consents).revoke(userId, consentId);
        consentMvc.perform(delete("/api/me/finance/consents/{consentId}", consentId)
                        .cookie(
                                new jakarta.servlet.http.Cookie(SessionCookieFactory.COOKIE_NAME, "session-proof"),
                                new jakarta.servlet.http.Cookie("AIRA_FINANCE_ACCESS", "grant-proof")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));
    }

    @Test
    void revokingFinanceAccessClearsGrantCookie() throws Exception {
        accessMvc.perform(delete("/api/me/finance/access")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieFactory.COOKIE_NAME, "session-proof")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("AIRA_FINANCE_ACCESS="),
                        containsString("Path=/api/me/finance"),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"))));
        verify(grants).revoke(userId, "session-proof");
    }
    @Test
    void destructiveDeleteFailsClosedWithoutFinanceGrant() throws Exception {
        doThrow(new FinanceAccessRequiredException()).when(grants).requireAccess(eq(userId), any(), any());
        dataMvc.perform(delete("/api/me/finance/data")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieFactory.COOKIE_NAME, "session-proof")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(deletion);
    }

    @Test
    void destructiveDeleteClearsFinanceGrantCookie() throws Exception {
        doNothing().when(grants).requireAccess(eq(userId), any(), any());
        dataMvc.perform(delete("/api/me/finance/data")
                        .cookie(
                                new jakarta.servlet.http.Cookie(SessionCookieFactory.COOKIE_NAME, "session-proof"),
                                new jakarta.servlet.http.Cookie("AIRA_FINANCE_ACCESS", "grant-proof")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", allOf(
                        containsString("AIRA_FINANCE_ACCESS="),
                        containsString("Max-Age=0"),
                        containsString("HttpOnly"))));
        verify(deletion).deleteAll(userId);
    }

}
