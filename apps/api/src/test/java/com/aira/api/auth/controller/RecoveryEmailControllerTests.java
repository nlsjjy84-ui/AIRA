package com.aira.api.auth.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.service.RecoveryEmailVerificationService;
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

class RecoveryEmailControllerTests {
    RecoveryEmailVerificationService service = mock(RecoveryEmailVerificationService.class);
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
        mvc = MockMvcBuilders.standaloneSetup(new RecoveryEmailController(service))
                .setCustomArgumentResolvers(principal)
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    void requestReturnsAcceptedWithoutReturningToken() throws Exception {
        mvc.perform(post("/api/auth/recovery-email/verifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"User@example.com\"}"))
                .andExpect(status().isAccepted());
        verify(service).request(userId, "User@example.com");
    }

    @Test
    void confirmReturnsSameEmptyResponseForAnyInternalOutcome() throws Exception {
        when(service.confirm("raw-token")).thenReturn(false);
        mvc.perform(post("/api/auth/recovery-email/verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"raw-token\"}"))
                .andExpect(status().isNoContent());
        verify(service).confirm("raw-token");
    }
}
