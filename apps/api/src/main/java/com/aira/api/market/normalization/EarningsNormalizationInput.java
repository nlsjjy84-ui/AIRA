package com.aira.api.market.normalization;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EarningsNormalizationInput(
        UUID subjectEntityId,
        UUID evidenceId,
        LocalDate reportingPeriodEnd,
        String neutralTitle,
        OffsetDateTime occurredAt) {

    public EarningsNormalizationInput {
        if (subjectEntityId == null || evidenceId == null || reportingPeriodEnd == null
                || neutralTitle == null || neutralTitle.isBlank()) {
            throw new IllegalArgumentException("Earnings normalization input values are required");
        }
    }
}
