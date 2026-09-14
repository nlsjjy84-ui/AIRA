package com.aira.api.market.ecos;

import java.util.List;
import java.util.UUID;

public record EcosRealGdpFactIngestionResult(
        UUID evidenceId,
        UUID statisticalSeriesId,
        List<UUID> factIds) {

    public EcosRealGdpFactIngestionResult {
        if (evidenceId == null || statisticalSeriesId == null || factIds == null) {
            throw new IllegalArgumentException("REAL_GDP ingestion result identifiers are required");
        }
        factIds = List.copyOf(factIds);
        if (factIds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("REAL_GDP fact identifiers must not contain null");
        }
    }
}
