package com.aira.api.delivery.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.delivery.dto.AlertResponse;
import com.aira.api.delivery.dto.AlertResponse.EvidenceReference;
import com.aira.api.delivery.dto.AlertResponse.Item;
import com.aira.api.delivery.dto.RelatedCompany;
import com.aira.api.delivery.service.AlertNotFoundException;
import com.aira.api.delivery.service.InAppAlertService;
import java.time.OffsetDateTime;
import java.util.List;
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

class InAppAlertControllerTests {
    private final UUID userId = UUID.randomUUID();
    private final InAppAlertService service = mock(InAppAlertService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new InAppAlertController(service))
                .setCustomArgumentResolvers(new PrincipalResolver(
                        new AiraPrincipal(userId, "AlertUser")))
                .build();
    }

    @Test
    void returnsExactOwnedAssessmentAndCanonicalEvidenceIdentity() throws Exception {
        UUID alertId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID assessmentId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        OffsetDateTime occurredAt = OffsetDateTime.parse("2026-08-01T00:00:00Z");
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-02T00:00:00Z");
        OffsetDateTime sentAt = OffsetDateTime.parse("2026-08-03T00:00:00Z");
        Item item = new Item(alertId,
                List.of(new RelatedCompany(UUID.randomUUID(), "Alert Company")),
                eventId, "Exact event", "EARNINGS", occurredAt, assessmentId,
                "interest-new-event-v1", "NEW_ASSESSMENT", "alert-v1", "RULE",
                "MEDIUM", "Exact summary", "HIGH", "Known uncertainty", completedAt,
                sentAt, sentAt, List.of(new EvidenceReference(evidenceId, "ALERT-E-1",
                        "https://official.example/alert", "Official", occurredAt, 2)));
        when(service.findOwned(userId, alertId)).thenReturn(item);

        mvc.perform(get("/api/me/alerts/{id}", alertId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alertId").value(alertId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.assessmentId").value(assessmentId.toString()))
                .andExpect(jsonPath("$.policyVersion").value("interest-new-event-v1"))
                .andExpect(jsonPath("$.reasonCode").value("NEW_ASSESSMENT"))
                .andExpect(jsonPath("$.analysisVersion").value("alert-v1"))
                .andExpect(jsonPath("$.method").value("RULE"))
                .andExpect(jsonPath("$.importance").value("MEDIUM"))
                .andExpect(jsonPath("$.confidence").value("HIGH"))
                .andExpect(jsonPath("$.uncertainty").value("Known uncertainty"))
                .andExpect(jsonPath("$.completedAt").value(completedAt.toInstant().toString()))
                .andExpect(jsonPath("$.occurredAt").value(occurredAt.toInstant().toString()))
                .andExpect(jsonPath("$.sentAt").value(sentAt.toInstant().toString()))
                .andExpect(jsonPath("$.evidence[0].evidenceId").value(evidenceId.toString()))
                .andExpect(jsonPath("$.evidence[0].revision").value(2));

        verify(service).findOwned(userId, alertId);
    }

    @Test
    void foreignOrMissingAlertUsesTheSameNotFoundContract() throws Exception {
        UUID alertId = UUID.randomUUID();
        when(service.findOwned(userId, alertId)).thenThrow(new AlertNotFoundException());

        mvc.perform(get("/api/me/alerts/{id}", alertId))
                .andExpect(status().isNotFound());
    }

    private record PrincipalResolver(AiraPrincipal principal)
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
