package com.aira.api.market.krx;

import java.time.LocalDate;

public final class KrxCurrentExactMissException extends IllegalStateException {
    private final LocalDate tradingDate;
    public KrxCurrentExactMissException() { this(null); }
    public KrxCurrentExactMissException(LocalDate tradingDate) {
        super("Exact official KRX D Fact is unavailable");
        this.tradingDate = tradingDate;
    }
    public LocalDate tradingDate() { return tradingDate; }
}
