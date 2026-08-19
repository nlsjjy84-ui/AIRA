package com.aira.api.market.opendart;

import com.aira.api.market.domain.FactPredicate;

public record OpenDartAnnualCfsRequest(
        String corpCode,
        int businessYear,
        FactPredicate predicate) {

    public OpenDartAnnualCfsRequest {
        if (corpCode == null || !corpCode.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("OpenDART corp code must contain exactly 8 digits");
        }
        if (businessYear < 1900 || businessYear > 9999) {
            throw new IllegalArgumentException("Business year is invalid");
        }
        if (predicate == null) {
            throw new IllegalArgumentException("Fact predicate is required");
        }
    }
}
