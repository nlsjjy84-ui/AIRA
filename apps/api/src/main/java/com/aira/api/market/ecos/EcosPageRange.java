package com.aira.api.market.ecos;

public record EcosPageRange(long startRow, long endRow) {
    public EcosPageRange {
        if (startRow < 1) {
            throw new IllegalArgumentException("startRow must be at least 1");
        }
        if (endRow < startRow) {
            throw new IllegalArgumentException("endRow must be at least startRow");
        }
    }
}
