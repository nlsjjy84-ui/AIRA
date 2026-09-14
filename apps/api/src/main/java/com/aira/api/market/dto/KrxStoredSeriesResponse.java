package com.aira.api.market.dto;

import com.aira.api.market.domain.FactPredicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record KrxStoredSeriesResponse(CanonicalDataState state, String reason, UUID securityId,
        FactPredicate predicate, LocalDate from, LocalDate to, List<Point> points) {
    public record Point(LocalDate tradingDate, UUID factId, BigDecimal value,
            List<UUID> evidenceIds, String evidenceExternalId) {}
}
