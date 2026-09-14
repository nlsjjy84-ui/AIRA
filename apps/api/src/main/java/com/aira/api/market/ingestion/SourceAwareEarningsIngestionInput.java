package com.aira.api.market.ingestion;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.EventOrigin;
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
        String assertionLocator,
        EventOrigin origin) {

    public SourceAwareEarningsIngestionInput(SourceRegistration source, EvidenceRegistration evidence,
            UUID subjectEntityId, LocalDate reportingPeriodEnd, String neutralTitle,
            OffsetDateTime occurredAt, FactPredicate predicate, BigDecimal numberValue,
            String currencyCode, LocalDate periodStart, LocalDate periodEnd, String assertionLocator) {
        this(source, evidence, subjectEntityId, reportingPeriodEnd, neutralTitle, occurredAt,
                predicate, numberValue, currencyCode, periodStart, periodEnd, assertionLocator,
                EventOrigin.LEGACY_UNKNOWN);
    }

    public SourceAwareEarningsIngestionInput {
        if (source == null || evidence == null || origin == null) {
            throw new IllegalArgumentException("Source and evidence registrations are required");
        }
    }
}
