package com.aira.api.market.query;

import com.aira.api.market.domain.FactPredicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CompanyFinancialFactView(
        UUID companyId,
        FactPredicate predicate,
        BigDecimal value,
        String currency,
        LocalDate periodStart,
        LocalDate periodEnd,
        OffsetDateTime publishedAt,
        OffsetDateTime collectedAt,
        String sourceName,
        UUID evidenceId,
        String evidenceExternalId,
        String evidenceOriginalUrl) {
}
