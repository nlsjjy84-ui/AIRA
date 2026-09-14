package com.aira.api.market.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.aira.api.market.dto.PublicEventDetailResponse;
import com.aira.api.market.query.PublicEventDetailQuery;
import com.aira.api.market.query.PublicEventNotFoundException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PublicEventDetailControllerTests {
    private final PublicEventDetailQuery query = mock(PublicEventDetailQuery.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.reset(query);
        mvc = MockMvcBuilders.standaloneSetup(new PublicEventDetailController(query)).build();
    }

    @Test
    void exposesSeparatedFactualEvidenceAndAssessmentLayers() throws Exception {
        UUID eventId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        var evidence = new PublicEventDetailResponse.Evidence(evidenceId, "Official", "EXT",
                "Evidence title", "stored://url", OffsetDateTime.parse("2026-08-24T00:00:00Z"));
        var assessment = new PublicEventDetailResponse.Assessment(UUID.randomUUID(), "summary",
                "uncertainty", "HIGH", "MEDIUM", "UNSPECIFIED", "RULE", "v1",
                List.of(evidence));
        when(query.find(eventId)).thenReturn(new PublicEventDetailResponse(eventId, "EARNINGS",
                "Factual title", OffsetDateTime.parse("2026-08-25T00:00:00Z"),
                List.of(new PublicEventDetailResponse.Company(UUID.randomUUID(), "회사")),
                List.of(evidence), assessment));

        mvc.perform(get("/api/events/{eventId}", eventId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Factual title"))
                .andExpect(jsonPath("$.eventEvidence[0].evidenceId").value(evidenceId.toString()))
                .andExpect(jsonPath("$.assessment.summary").value("summary"))
                .andExpect(jsonPath("$.assessment.evidence[0].evidenceId").value(evidenceId.toString()));
    }

    @Test
    void mapsMissingOrIneligibleEventToNotFound() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(query.find(eventId)).thenThrow(new PublicEventNotFoundException());
        mvc.perform(get("/api/events/{eventId}", eventId)).andExpect(status().isNotFound());
    }

    @Test
    void materialEventWithoutAssessmentKeepsAssessmentAbsent() throws Exception {
        UUID eventId = UUID.randomUUID();
        when(query.find(eventId)).thenReturn(new PublicEventDetailResponse(eventId, "DISCLOSURE",
                "유상증자 결정", null, List.of(), List.of(), null));
        mvc.perform(get("/api/events/{eventId}", eventId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.assessment").doesNotExist());
    }
}
