package com.aira.api.market.opendart;

import com.aira.api.market.domain.EventType;
import com.aira.api.market.domain.EventOrigin;
import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.ingestion.EvidenceRegistration;
import java.time.OffsetDateTime;

public record ValidatedMaterialEvent(String endpointKey, String corpCode, String receiptNumber,
        EventType eventType, String neutralTitle, EvidenceRegistration structuredEvidence,
        OffsetDateTime observedAt, EventOrigin origin) {
    public ValidatedMaterialEvent(String endpointKey, String corpCode, String receiptNumber,
            EventType eventType, String neutralTitle, EvidenceRegistration structuredEvidence,
            OffsetDateTime observedAt) {
        this(endpointKey, corpCode, receiptNumber, eventType, neutralTitle, structuredEvidence,
                observedAt, EventOrigin.LEGACY_UNKNOWN);
    }
    public ValidatedMaterialEvent {
        if (endpointKey == null || !endpointKey.matches("[A-Za-z][A-Za-z0-9_]*")
                || corpCode == null || !corpCode.matches("[0-9]{8}")
                || receiptNumber == null || !receiptNumber.matches("[0-9]{14}")
                || eventType == null || eventType == EventType.EARNINGS
                || neutralTitle == null || neutralTitle.isBlank() || observedAt == null || origin == null
                || structuredEvidence == null || structuredEvidence.evidenceType() != EvidenceType.OFFICIAL_DATA
                || !(("OPENDART_MATERIAL:" + endpointKey + ":" + receiptNumber)
                    .equals(structuredEvidence.externalId()))
                || structuredEvidence.revision() != 1 || structuredEvidence.contentHash() == null
                || structuredEvidence.contentHash().length != 32
                || structuredEvidence.originalUrl() == null || structuredEvidence.originalUrl().isBlank()) {
            throw new IllegalArgumentException("Validated OpenDART material event and structured Evidence are required");
        }
    }
}
