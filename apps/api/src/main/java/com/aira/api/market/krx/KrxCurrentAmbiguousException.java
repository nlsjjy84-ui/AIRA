package com.aira.api.market.krx;

public final class KrxCurrentAmbiguousException extends IllegalStateException {
    public KrxCurrentAmbiguousException() { super("Exact official KRX D Fact is ambiguous"); }
}
