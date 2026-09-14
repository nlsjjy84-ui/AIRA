package com.aira.api.market.ecos;

import java.util.List;

public record EcosMetadataDiscoveryResult(
        long totalCount,
        int pageRequests,
        int worstCaseHttpAttempts,
        List<EcosMetadataItem> items) {
    public EcosMetadataDiscoveryResult {
        if (totalCount < 0 || pageRequests < 1 || worstCaseHttpAttempts < 1) {
            throw new IllegalArgumentException("discovery result counters are invalid");
        }
        items = List.copyOf(items);
        if (items.size() != totalCount) {
            throw new IllegalArgumentException("assembled metadata row count must equal totalCount");
        }
    }
}
