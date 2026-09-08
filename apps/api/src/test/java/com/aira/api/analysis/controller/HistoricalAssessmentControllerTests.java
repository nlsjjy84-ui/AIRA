package com.aira.api.analysis.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.aira.api.analysis.dto.HistoricalAssessmentResponse;
import com.aira.api.analysis.query.HistoricalAssessmentNotFoundException;
import com.aira.api.analysis.query.HistoricalAssessmentQuery;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class HistoricalAssessmentControllerTests {
    @Test
    void exposesExactAssessmentIdentityAndEvidenceIds() throws Exception {
        HistoricalAssessmentQuery query = mock(HistoricalAssessmentQuery.class);
        UUID assessmentId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        when(query.find(assessmentId)).thenReturn(new HistoricalAssessmentResponse(
                assessmentId, eventId, "v1", "RULE", "MEDIUM", "uncertainty",
                OffsetDateTime.parse("2026-01-01T00:00:00Z"), null, List.of(evidenceId)));
        var mvc = MockMvcBuilders.standaloneSetup(
                new HistoricalAssessmentController(query)).build();

        mvc.perform(get("/api/assessments/{assessmentId}", assessmentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assessmentId").value(assessmentId.toString()))
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.evidenceIds[0]").value(evidenceId.toString()));
    }

    @Test
    void mapsIneligibleAssessmentToNotFound() throws Exception {
        HistoricalAssessmentQuery query = mock(HistoricalAssessmentQuery.class);
        UUID assessmentId = UUID.randomUUID();
        when(query.find(assessmentId)).thenThrow(new HistoricalAssessmentNotFoundException());
        var mvc = MockMvcBuilders.standaloneSetup(
                new HistoricalAssessmentController(query)).build();

        mvc.perform(get("/api/assessments/{assessmentId}", assessmentId))
                .andExpect(status().isNotFound());
    }
}
