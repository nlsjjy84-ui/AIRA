package com.aira.api.market.ecos;

import java.util.List;

public record EcosStatisticSearchPage(
        long totalCount,
        List<EcosStatisticSearchObservation> observations) {
    public EcosStatisticSearchPage {
        if (totalCount < 0) {
            throw new IllegalArgumentException("totalCount must not be negative");
        }
        observations = List.copyOf(observations);
    }
}
