package com.aira.api.delivery.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BriefingResponse(UUID briefingId, String title, String status,
        String briefingType, OffsetDateTime periodStart, OffsetDateTime periodEnd,
        OffsetDateTime generatedAt, String emptyReason, List<Item> items) {
    public record Item(short displayOrder, List<RelatedCompany> companies,
            UUID eventId, String eventType, String eventTitle, OffsetDateTime occurredAt,
            UUID assessmentId, String analysisVersion, String summary, String uncertainty,
            String importance, String confidence, OffsetDateTime completedAt,
            List<EvidenceReference> evidence) {
        public Item {
            companies = List.copyOf(companies);
            evidence = List.copyOf(evidence);
        }
    }

    public record EvidenceReference(UUID evidenceId, String externalId, String originalUrl,
            String sourceName) {}

    public static BriefingResponse empty(String briefingType, OffsetDateTime periodStart,
            OffsetDateTime periodEnd, OffsetDateTime generatedAt, String emptyReason) {
        return new BriefingResponse(null, "내 브리핑", "EMPTY", briefingType,
                periodStart, periodEnd, generatedAt, emptyReason, List.of());
    }
}
