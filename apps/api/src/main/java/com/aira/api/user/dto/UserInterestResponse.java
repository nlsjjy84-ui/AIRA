package com.aira.api.user.dto;

import com.aira.api.market.domain.EntityType;
import com.aira.api.user.domain.InterestLevel;
import java.time.OffsetDateTime;
import java.util.UUID;

public record UserInterestResponse(
        UUID entityId,
        EntityType entityType,
        String canonicalName,
        String marketCode,
        String symbol,
        String countryCode,
        InterestLevel interestLevel,
        boolean alertEnabled,
        OffsetDateTime alertEnabledAt,
        OffsetDateTime createdAt) {}
