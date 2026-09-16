package com.aira.api.personalfinance.dto;

import java.util.List;

public record MonthlySpendingPatternResponse(
        String month,
        String previousMonth,
        String currencyCode,
        String method,
        SpendingChangeResponse total,
        List<SpendingChangeResponse> categories,
        List<String> limitations) {
}
