package com.aira.api.personalfinance.ai;

import java.math.BigDecimal;
import java.util.List;

/** Aggregate-only AI input. No user id, account id, card id or transaction id may enter this record. */
public record AiExplanationInput(
        String month,
        String previousMonth,
        String currencyCode,
        BigDecimal totalCurrent,
        BigDecimal totalPrevious,
        BigDecimal totalDelta,
        List<CategoryChange> categories) {

    public record CategoryChange(
            String category,
            BigDecimal current,
            BigDecimal previous,
            BigDecimal delta) {}
}
