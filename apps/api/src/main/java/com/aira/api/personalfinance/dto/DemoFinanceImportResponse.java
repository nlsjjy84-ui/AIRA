package com.aira.api.personalfinance.dto;

import java.time.LocalDate;
import java.util.UUID;

public record DemoFinanceImportResponse(
        UUID connectionId,
        boolean demoData,
        String providerKey,
        int accountCount,
        int insertedTransactionCount,
        LocalDate periodStart,
        LocalDate periodEnd) {
}
