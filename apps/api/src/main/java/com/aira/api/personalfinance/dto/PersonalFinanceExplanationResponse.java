package com.aira.api.personalfinance.dto;

import java.util.List;
import java.util.UUID;

public record PersonalFinanceExplanationResponse(
        String method,
        String explanation,
        UUID aiExecutionId,
        String providerKey,
        String modelKey,
        MonthlySpendingPatternResponse pattern,
        List<String> limitations) {
}
