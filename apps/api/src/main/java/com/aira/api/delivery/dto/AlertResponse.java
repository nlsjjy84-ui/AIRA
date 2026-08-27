package com.aira.api.delivery.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record AlertResponse(List<Item> alerts) {
    public record Item(UUID alertId, List<RelatedCompany> companies, UUID eventId,
            String eventTitle, String eventType, OffsetDateTime occurredAt, UUID assessmentId,
            String summary, String uncertainty, String sourceName, String evidenceExternalId,
            String evidenceOriginalUrl, OffsetDateTime createdAt, OffsetDateTime sentAt) {
        public Item {
            companies = List.copyOf(companies);
        }
    }
}
