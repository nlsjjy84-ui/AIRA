package com.aira.api.user.dto;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.dto.CanonicalDataState;
import java.time.LocalDate;
import java.util.UUID;

public record InterestEligibilityResponse(CanonicalDataState state, UUID entityId,
        EntityType entityType, boolean alreadyInterested, boolean newRegistrationAllowed,
        LocalDate officialTradingDate, String reason) {}
