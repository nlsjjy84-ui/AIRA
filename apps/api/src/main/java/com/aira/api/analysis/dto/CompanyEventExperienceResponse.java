package com.aira.api.analysis.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CompanyEventExperienceResponse(UUID companyId, List<EventExperience> events) {
    public record EventExperience(UUID eventId, String eventType, String title,
            OffsetDateTime occurredAt, String status, AssessmentExperience assessment,
            List<EvidenceExperience> evidence) {}
    public record AssessmentExperience(String importance, String summary, String confidence,
            String uncertainty, String timeHorizon, String method) {}
    public record EvidenceExperience(UUID evidenceId, String sourceName, String externalId,
            String originalUrl, String title, int revision) {}
}
