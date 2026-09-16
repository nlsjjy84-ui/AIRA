package com.aira.api.personalfinance;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.personalfinance.controller.PersonalFinanceExceptionHandler;
import com.aira.api.personalfinance.controller.PersonalFinancePatternController;
import com.aira.api.personalfinance.exception.FinanceAccessRequiredException;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.PersonalFinancePatternService;
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

class PersonalFinancePatternControllerTests {
    FinanceAccessGrantService grants = mock(FinanceAccessGrantService.class);
    PersonalFinancePatternService patterns = mock(PersonalFinancePatternService.class);
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
        mvc = MockMvcBuilders.standaloneSetup(new PersonalFinancePatternController(grants, patterns))
                .setCustomArgumentResolvers(principal)
                .setControllerAdvice(new PersonalFinanceExceptionHandler())
                .build();
    }

    @Test
    void patternReadFailsClosedWithoutFinanceGrant() throws Exception {
        doThrow(new FinanceAccessRequiredException())
                .when(grants).requireAccess(eq(userId), any(), any());
        mvc.perform(get("/api/me/finance/patterns")
                        .param("month", "2026-09").param("currency", "KRW"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(patterns);
    }
}
