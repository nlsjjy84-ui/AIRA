package com.aira.api.personalfinance.dto;

import com.aira.api.personalfinance.domain.FinanceConnectionSource;
import java.time.OffsetDateTime;
import java.util.UUID;

public record FinanceConsentResponse(
        UUID id,
        FinanceConnectionSource sourceType,
        String providerKey,
        String policyVersion,
        boolean allowAccounts,
        boolean allowTransactions,
        OffsetDateTime consentedAt,
        OffsetDateTime revokedAt) {
}
