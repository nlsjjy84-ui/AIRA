package com.aira.api.market.query;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PublicEventView(UUID eventId, UUID companyId, String companyName,
        String eventType, String title, OffsetDateTime occurredAt) {}
