package com.aira.api.market.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PublicEventDetailResponse(UUID eventId, String eventType, String title,
        OffsetDateTime occurredAt, List<Company> companies, List<Evidence> eventEvidence,
        Assessment assessment) {
    public record Company(UUID companyId, String companyName) {}
    public record Evidence(UUID evidenceId, String sourceName, String externalId, String title,
            String originalUrl, OffsetDateTime publishedAt) {}
    public record Assessment(UUID assessmentId, String summary, String uncertainty,
            String confidence, String importance, String timeHorizon, String method,
            String analysisVersion, List<Evidence> evidence) {}
}
