package com.aira.api.market.normalization;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.aira.api.market.domain.EventOrigin;

public record EarningsNormalizationInput(
        UUID subjectEntityId,
        UUID evidenceId,
        LocalDate reportingPeriodEnd,
        String neutralTitle,
        OffsetDateTime occurredAt, EventOrigin origin) {

    public EarningsNormalizationInput(UUID subjectEntityId, UUID evidenceId, LocalDate reportingPeriodEnd,
            String neutralTitle, OffsetDateTime occurredAt) {
        this(subjectEntityId, evidenceId, reportingPeriodEnd, neutralTitle, occurredAt,
                EventOrigin.LEGACY_UNKNOWN);
    }

    public EarningsNormalizationInput {
        if (subjectEntityId == null || evidenceId == null || reportingPeriodEnd == null
                || neutralTitle == null || neutralTitle.isBlank() || origin == null) {
            throw new IllegalArgumentException("Earnings normalization input values are required");
        }
    }
}
