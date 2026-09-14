package com.aira.api.market.ecos;

public record EcosMetadataItem(
        String statCode,
        String statName,
        String groupCode,
        String groupName,
        String itemCode,
        String itemName,
        String parentItemCode,
        String parentItemName,
        String cycle,
        String startTime,
        String endTime,
        long dataCount,
        String unitName,
        String weight) {
}
