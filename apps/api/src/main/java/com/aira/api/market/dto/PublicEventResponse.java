package com.aira.api.market.dto;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

public record PublicEventResponse(UUID eventId, List<Company> companies,
        String eventType, String title, OffsetDateTime occurredAt) {
    public record Company(UUID companyId, String companyName) {}
}
