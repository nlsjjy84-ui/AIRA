package com.aira.api.analysis.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record HistoricalAssessmentResponse(UUID assessmentId, UUID eventId,
        String analysisVersion, String method, String confidence, String uncertainty,
        OffsetDateTime completedAt, UUID supersedesAssessmentId, List<UUID> evidenceIds) {
    public HistoricalAssessmentResponse {
        evidenceIds = List.copyOf(evidenceIds);
    }
}
