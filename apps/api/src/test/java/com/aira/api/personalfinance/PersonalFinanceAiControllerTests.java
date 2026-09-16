package com.aira.api.personalfinance;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.personalfinance.controller.PersonalFinanceAiController;
import com.aira.api.personalfinance.controller.PersonalFinanceExceptionHandler;
import com.aira.api.personalfinance.exception.FinanceAccessRequiredException;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.PersonalFinanceAiExplanationService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class PersonalFinanceAiControllerTests {
    @Test
    void financeGrantIsRequiredBeforeCostBearingAiCall() throws Exception {
        UUID userId = UUID.randomUUID();
        FinanceAccessGrantService grants = mock(FinanceAccessGrantService.class);
        PersonalFinanceAiExplanationService explanations = mock(PersonalFinanceAiExplanationService.class);
        doThrow(new FinanceAccessRequiredException()).when(grants)
                .requireAccess(eq(userId), any(), any());

        HandlerMethodArgumentResolver principal = new HandlerMethodArgumentResolver() {
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
            }
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                    NativeWebRequest request, WebDataBinderFactory factory) {
                return new AiraPrincipal(userId, "AiraUser");
            }
        };
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new PersonalFinanceAiController(grants, explanations))
                .setCustomArgumentResolvers(principal)
                .setControllerAdvice(new PersonalFinanceExceptionHandler())
                .build();

        mvc.perform(post("/api/me/finance/ai/explanation")
                        .param("month", "2026-09").param("currency", "KRW")
                        .cookie(new jakarta.servlet.http.Cookie(SessionCookieFactory.COOKIE_NAME, "session")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(explanations);
    }
}
