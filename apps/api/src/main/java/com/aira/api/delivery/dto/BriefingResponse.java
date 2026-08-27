package com.aira.api.delivery.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record BriefingResponse(UUID briefingId, String title, String status,
        String briefingType, OffsetDateTime periodStart, OffsetDateTime periodEnd,
        OffsetDateTime generatedAt, List<Item> items) {
    public record Item(short displayOrder, List<RelatedCompany> companies,
            UUID eventId, String eventType, String eventTitle, OffsetDateTime occurredAt,
            UUID assessmentId, String summary, String uncertainty, String importance,
            String confidence, String sourceName, String evidenceExternalId,
            String evidenceOriginalUrl) {
        public Item {
            companies = List.copyOf(companies);
        }
    }

    public static BriefingResponse empty(String briefingType, OffsetDateTime periodStart,
            OffsetDateTime periodEnd, OffsetDateTime generatedAt) {
        return new BriefingResponse(null, "내 브리핑", "EMPTY", briefingType,
                periodStart, periodEnd, generatedAt, List.of());
    }
}
