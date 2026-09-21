package com.aira.api.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MarketIndexBoardResponse(List<IndexView> indices) {
    public record IndexView(String marketCode, CanonicalDataState state, String reason,
            LocalDate tradingDate, BigDecimal close, BigDecimal change, BigDecimal changeRate,
            UUID evidenceId, String sourceName, String originalUrl) {}
}
