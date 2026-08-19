package com.aira.api.market.ingestion;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.service.SourceRegistration;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record SourceAwareEarningsIngestionInput(
        SourceRegistration source,
        EvidenceRegistration evidence,
        UUID subjectEntityId,
        LocalDate reportingPeriodEnd,
        String neutralTitle,
        OffsetDateTime occurredAt,
        FactPredicate predicate,
        BigDecimal numberValue,
        String currencyCode,
        LocalDate periodStart,
        LocalDate periodEnd,
        String assertionLocator) {

    public SourceAwareEarningsIngestionInput {
        if (source == null || evidence == null) {
            throw new IllegalArgumentException("Source and evidence registrations are required");
        }
    }
}
