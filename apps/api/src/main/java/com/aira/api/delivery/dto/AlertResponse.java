package com.aira.api.delivery.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AlertResponse(String emptyReason, List<Item> alerts) {
    public AlertResponse {
        alerts = List.copyOf(alerts);
    }

    public record Item(UUID alertId, List<RelatedCompany> companies, UUID eventId,
            String eventTitle, String eventType, OffsetDateTime occurredAt, UUID assessmentId,
            String policyVersion, String reasonCode, String analysisVersion, String method,
            String importance, String summary, String confidence, String uncertainty,
            OffsetDateTime completedAt, OffsetDateTime createdAt, OffsetDateTime sentAt,
            List<EvidenceReference> evidence) {
        public Item {
            companies = List.copyOf(companies);
            evidence = List.copyOf(evidence);
        }
    }

    public record EvidenceReference(UUID evidenceId, String externalId, String originalUrl,
            String sourceName, OffsetDateTime publishedAt, int revision) {}
}
