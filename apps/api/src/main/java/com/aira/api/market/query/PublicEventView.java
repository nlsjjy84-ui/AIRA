package com.aira.api.market.query;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;
import com.aira.api.market.dto.PublicEventResponse.Company;

public record PublicEventView(UUID eventId, List<Company> companies,
        String eventType, String title, OffsetDateTime occurredAt) {}
