package com.aira.api.personalfinance;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.personalfinance.controller.PersonalFinanceBudgetController;
import com.aira.api.personalfinance.controller.PersonalFinanceExceptionHandler;
import com.aira.api.personalfinance.domain.BudgetCategory;
import com.aira.api.personalfinance.dto.BudgetResponse;
import com.aira.api.personalfinance.exception.FinanceAccessRequiredException;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.PersonalFinanceBudgetService;
import java.math.BigDecimal;
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
class PersonalFinanceBudgetControllerTests {
    FinanceAccessGrantService grants = mock(FinanceAccessGrantService.class);
    PersonalFinanceBudgetService budgets = mock(PersonalFinanceBudgetService.class);
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
                return new AiraPrincipal(userId, "AiraUser");
            }
        };
        mvc = MockMvcBuilders.standaloneSetup(new PersonalFinanceBudgetController(grants, budgets))
                .setCustomArgumentResolvers(principal)
                .setControllerAdvice(new PersonalFinanceExceptionHandler())
                .build();
    }

    @Test
    void budgetWriteFailsClosedWithoutFinanceGrant() throws Exception {
        doThrow(new FinanceAccessRequiredException())
                .when(grants).requireAccess(eq(userId), any(), any());
        mvc.perform(post("/api/me/finance/budgets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\",\"category\":\"TOTAL\",\"amount\":1500000,\"currencyCode\":\"KRW\"}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(budgets);
    }

    @Test
    void budgetWriteReturnsUserAuthoredValue() throws Exception {
        doNothing().when(grants).requireAccess(eq(userId), any(), any());
        UUID id = UUID.randomUUID();
        when(budgets.upsert(eq(userId), any())).thenReturn(new BudgetResponse(
                id, "2026-09", BudgetCategory.TOTAL, new BigDecimal("1500000.00"), "KRW"));

        mvc.perform(post("/api/me/finance/budgets")
                        .cookie(new jakarta.servlet.http.Cookie("AIRA_SESSION", "session-proof"),
                                new jakarta.servlet.http.Cookie("AIRA_FINANCE_ACCESS", "grant-proof"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"month\":\"2026-09\",\"category\":\"TOTAL\",\"amount\":1500000,\"currencyCode\":\"KRW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.category").value("TOTAL"))
                .andExpect(jsonPath("$.currencyCode").value("KRW"));
    }

    @Test
    void summaryReadAlsoRequiresFinanceGrant() throws Exception {
        doThrow(new FinanceAccessRequiredException())
                .when(grants).requireAccess(eq(userId), any(), any());
        mvc.perform(get("/api/me/finance/summary")
                        .param("month", "2026-09").param("currency", "KRW"))
                .andExpect(status().isForbidden());
        verify(budgets, never()).summary(any(), any(), any());
    }
}
