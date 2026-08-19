package com.aira.api.market.ingestion;

import java.util.UUID;

public record IngestionReceipt(
        UUID sourceId,
        UUID evidenceId,
        UUID eventId,
        UUID factId) {

    public IngestionReceipt {
        if (sourceId == null || evidenceId == null || eventId == null || factId == null) {
            throw new IllegalArgumentException("Ingestion receipt identifiers are required");
        }
    }

    static IngestionReceipt from(SourceAwareIngestionResult result) {
        if (result == null) {
            throw new IllegalStateException("Ingestion result is required");
        }
        return new IngestionReceipt(result.source().getId(), result.evidence().getId(),
                result.event().getId(), result.fact().getId());
    }
}
