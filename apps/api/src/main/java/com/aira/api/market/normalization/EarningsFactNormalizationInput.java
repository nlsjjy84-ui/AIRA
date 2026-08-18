package com.aira.api.market.normalization;

import com.aira.api.market.domain.FactPredicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record EarningsFactNormalizationInput(
        UUID eventId,
        UUID subjectEntityId,
        UUID evidenceId,
        FactPredicate predicate,
        BigDecimal numberValue,
        String currencyCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        String locator) {

    public EarningsFactNormalizationInput {
        if (eventId == null || subjectEntityId == null || evidenceId == null
                || predicate == null || numberValue == null || currencyCode == null
                || !currencyCode.matches("[A-Z]{3}") || periodStart == null
                || periodEnd == null || periodStart.isAfter(periodEnd)
                || locator == null || locator.isBlank()) {
            throw new IllegalArgumentException("Earnings fact normalization values are invalid");
        }
        if (predicate != FactPredicate.REVENUE
                && predicate != FactPredicate.OPERATING_INCOME) {
            throw new IllegalArgumentException("Unsupported earnings fact predicate");
        }
    }
}
