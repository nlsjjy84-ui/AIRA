package com.aira.api.market.ecos;

public record EcosObservationBudget(
        long maxQuarters,
        int maxPageRequests,
        int maxHttpAttempts) {
    public EcosObservationBudget {
        if (maxQuarters < 1) {
            throw new IllegalArgumentException("maxQuarters must be at least 1");
        }
        if (maxPageRequests < 1) {
            throw new IllegalArgumentException("maxPageRequests must be at least 1");
        }
        if (maxHttpAttempts < EcosRequestExecutionGuard.MAX_ATTEMPTS) {
            throw new IllegalArgumentException("maxHttpAttempts must cover one page worst case");
        }
    }
}
