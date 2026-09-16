package com.aira.api.personalfinance;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.personalfinance.controller.DemoFinanceImportController;
import com.aira.api.personalfinance.controller.PersonalFinanceExceptionHandler;
import com.aira.api.personalfinance.dto.DemoFinanceImportResponse;
import com.aira.api.personalfinance.exception.FinanceAccessRequiredException;
import com.aira.api.personalfinance.exception.FinanceConsentRequiredException;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.DemoFinanceImportService;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class DemoFinanceImportControllerTests {
    FinanceAccessGrantService grants = mock(FinanceAccessGrantService.class);
    DemoFinanceImportService imports = mock(DemoFinanceImportService.class);
    UUID userId = UUID.randomUUID();
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        HandlerMethodArgumentResolver principal = new HandlerMethodArgumentResolver() {
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, WebDataBinderFactory factory) {
                return new AiraPrincipal(userId, "DemoUser");
            }
        };
        mvc = MockMvcBuilders.standaloneSetup(new DemoFinanceImportController(grants, imports))
                .setCustomArgumentResolvers(principal)
                .setControllerAdvice(new PersonalFinanceExceptionHandler())
                .build();
    }

    @Test
    void financeGrantIsRequiredBeforeDemoImport() throws Exception {
        doThrow(new FinanceAccessRequiredException())
                .when(grants).requireAccess(eq(userId), any(), any());

        mvc.perform(post("/api/me/finance/demo-import"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(imports);
    }

    @Test
    void missingDemoConsentReturnsConflict() throws Exception {
        doNothing().when(grants).requireAccess(eq(userId), any(), any());
        when(imports.importDemo(userId)).thenThrow(new FinanceConsentRequiredException());

        mvc.perform(post("/api/me/finance/demo-import"))
                .andExpect(status().isConflict());
    }

    @Test
    void successfulImportIsExplicitlyLabeledAsDemo() throws Exception {
        doNothing().when(grants).requireAccess(eq(userId), any(), any());
        UUID connectionId = UUID.randomUUID();
        when(imports.importDemo(userId)).thenReturn(new DemoFinanceImportResponse(
                connectionId, true, DemoFinanceImportService.PROVIDER_KEY, 2, 15,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 9, 30)));

        mvc.perform(post("/api/me/finance/demo-import"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("\"demoData\":true")))
                .andExpect(content().string(containsString("\"providerKey\":\"AIRA_DEMO_V1\"")))
                .andExpect(content().string(containsString("\"accountCount\":2")))
                .andExpect(content().string(containsString("\"insertedTransactionCount\":15")));
        verify(imports).importDemo(userId);
    }
}
