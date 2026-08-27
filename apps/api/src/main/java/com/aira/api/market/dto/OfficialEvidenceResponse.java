package com.aira.api.market.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record OfficialEvidenceResponse(UUID evidenceId, String evidenceType, String externalId,
        String title, String originalUrl, OffsetDateTime publishedAt, OffsetDateTime collectedAt,
        int revision, String locator, String excerpt, Source source) {
    public record Source(UUID sourceId, String sourceName, String sourceType,
            String canonicalDomain) {}
}
