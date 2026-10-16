package com.aira.api.market.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MarketIndexBoardResponse(List<IndexView> indices) {
    /** open: 같은 날 KRX 일별 자료의 시가(장 시작 지수). 저장된 값이 없으면 null. */
    public record IndexView(String marketCode, CanonicalDataState state, String reason,
            LocalDate tradingDate, BigDecimal close, BigDecimal change, BigDecimal changeRate,
            UUID evidenceId, String sourceName, String originalUrl, BigDecimal open) {
        public IndexView(String marketCode, CanonicalDataState state, String reason,
                LocalDate tradingDate, BigDecimal close, BigDecimal change, BigDecimal changeRate,
                UUID evidenceId, String sourceName, String originalUrl) {
            this(marketCode, state, reason, tradingDate, close, change, changeRate, evidenceId, sourceName, originalUrl, null);
        }
    }
}
