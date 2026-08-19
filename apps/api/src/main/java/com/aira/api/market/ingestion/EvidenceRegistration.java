package com.aira.api.market.ingestion;

import com.aira.api.market.domain.EvidenceType;
import java.time.OffsetDateTime;

public record EvidenceRegistration(
        EvidenceType evidenceType,
        String externalId,
        String originalUrl,
        String title,
        byte[] contentHash,
        String locator,
        OffsetDateTime publishedAt,
        OffsetDateTime collectedAt,
        int revision) {

    public EvidenceRegistration {
        if (contentHash != null) {
            contentHash = contentHash.clone();
        }
    }

    @Override
    public byte[] contentHash() {
        return contentHash == null ? null : contentHash.clone();
    }
}
