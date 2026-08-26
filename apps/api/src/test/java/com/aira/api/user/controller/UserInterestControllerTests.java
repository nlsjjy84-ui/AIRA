package com.aira.api.user.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.user.exception.InterestEntityNotFoundException;
import com.aira.api.user.exception.InvalidInterestEntityException;
import com.aira.api.user.service.UserInterestService;
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

class UserInterestControllerTests {
    private final UUID userId = UUID.randomUUID();
    private final UserInterestService service = mock(UserInterestService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        AiraPrincipal principal = new AiraPrincipal(userId, "AiraUser");
        mvc = MockMvcBuilders.standaloneSetup(new UserInterestController(service))
                .setControllerAdvice(new UserInterestExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticatedPrincipalResolver(principal))
                .build();
    }

    @Test
    void returnsInvalidInterestEntityContractForNonCompanyEntity() throws Exception {
        UUID entityId = UUID.randomUUID();
        when(service.add(userId, entityId))
                .thenThrow(new InvalidInterestEntityException("Entity type is not supported in v1"));

        mvc.perform(post("/api/me/interests/{entityId}", entityId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INTEREST_ENTITY"));
    }

    @Test
    void preservesMissingEntityContract() throws Exception {
        UUID entityId = UUID.randomUUID();
        when(service.add(userId, entityId)).thenThrow(new InterestEntityNotFoundException());

        mvc.perform(post("/api/me/interests/{entityId}", entityId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"));
    }

    private record AuthenticatedPrincipalResolver(AiraPrincipal principal)
            implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(AuthenticationPrincipal.class)
                    && parameter.getParameterType().equals(AiraPrincipal.class);
        }

        @Override
        public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                NativeWebRequest request, WebDataBinderFactory binderFactory) {
            return principal;
        }
    }
}
