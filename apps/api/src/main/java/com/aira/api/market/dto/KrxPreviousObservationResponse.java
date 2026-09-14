package com.aira.api.market.dto;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.dto.KrxStoredSeriesResponse.Point;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record KrxPreviousObservationResponse(CanonicalDataState state, String reason,
        UUID securityId, FactPredicate predicate, LocalDate suppliedCurrentDate,
        UUID suppliedCurrentFactId, Point current, Point previous,
        BigDecimal changeAmount, BigDecimal changePercent, String percentReason) {}
