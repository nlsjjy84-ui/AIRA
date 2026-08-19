package com.aira.api.market.opendart;

import com.aira.api.market.domain.FactPredicate;

public record OpenDartAnnualCfsRequest(
        OpenDartAnnualCfsContext context,
        FactPredicate predicate) {

    public OpenDartAnnualCfsRequest {
        if (context == null || predicate == null) {
            throw new IllegalArgumentException("Fact predicate is required");
        }
    }
}
