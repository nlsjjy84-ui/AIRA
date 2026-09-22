package com.aira.api.market.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record EconomicIndicatorResponse(CanonicalDataState state, String reason,
        String seriesName, String unitName, String sourceName, List<ObservationView> observations) {
    public record ObservationView(String period, BigDecimal value, UUID evidenceId) {}
}
