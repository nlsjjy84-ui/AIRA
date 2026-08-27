package com.aira.api.market.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicEventResponse(UUID eventId, UUID companyId, String companyName,
        String eventType, String title, OffsetDateTime occurredAt) {}
