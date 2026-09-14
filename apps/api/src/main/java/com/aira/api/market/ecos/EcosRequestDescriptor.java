package com.aira.api.market.ecos;

public record EcosRequestDescriptor(long startRow, long endRow, String statCode) {
    public static final String OPERATION = "StatisticItemList";

    public EcosRequestDescriptor {
        new EcosPageRange(startRow, endRow);
        if (statCode == null || statCode.isBlank()) {
            throw new IllegalArgumentException("statCode must not be blank");
        }
    }

    public String operation() {
        return OPERATION;
    }
}
