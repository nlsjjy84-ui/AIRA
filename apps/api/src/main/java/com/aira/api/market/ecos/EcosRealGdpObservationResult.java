package com.aira.api.market.ecos;

import java.util.List;

public record EcosRealGdpObservationResult(
        String startTime,
        String endTime,
        long requestedQuarters,
        long totalCount,
        int pageRequests,
        int worstCaseHttpAttempts,
        List<EcosStatisticSearchObservation> observations) {
    public EcosRealGdpObservationResult {
        if (requestedQuarters < 1 || totalCount < 0 || pageRequests < 1 || worstCaseHttpAttempts < 1) {
            throw new IllegalArgumentException("observation result counters are invalid");
        }
        observations = List.copyOf(observations);
        if (observations.size() != totalCount) {
            throw new IllegalArgumentException("assembled observation count must equal totalCount");
        }
    }
}
