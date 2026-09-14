package com.aira.api.market.ecos;

import java.math.BigDecimal;

public record EcosStatisticSearchObservation(
        String statCode,
        String statName,
        String itemCode1,
        String itemName1,
        String itemCode2,
        String itemName2,
        String itemCode3,
        String itemName3,
        String itemCode4,
        String itemName4,
        String unitName,
        String weight,
        String time,
        String rawDataValue,
        BigDecimal numericValue) {
}
