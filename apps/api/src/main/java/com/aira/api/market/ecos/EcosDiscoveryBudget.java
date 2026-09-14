package com.aira.api.market.ecos;

public record EcosDiscoveryBudget(
        long maxRows,
        int maxPageRequests,
        int maxHttpAttempts) {
    public EcosDiscoveryBudget {
        if (maxRows < 1000) {
            throw new IllegalArgumentException("maxRows must authorize the mandatory first 1000-row page");
        }
        if (maxPageRequests < 1) {
            throw new IllegalArgumentException("maxPageRequests must be at least 1");
        }
        if (maxHttpAttempts < EcosRequestExecutionGuard.MAX_ATTEMPTS) {
            throw new IllegalArgumentException("maxHttpAttempts must cover the first page worst case");
        }
    }
}
