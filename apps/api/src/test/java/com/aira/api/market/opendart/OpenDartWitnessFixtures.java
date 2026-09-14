package com.aira.api.market.opendart;

import java.util.List;

// Synthetic contract fixtures, not captured official filings.
final class OpenDartWitnessFixtures {
    static OpenDartFinancialRow value(String corp, String receipt, String account, String amount) {
        return new OpenDartFinancialRow(receipt, "2025", "11011", corp, null, "IS", "display",
                account, "ignored label", null, null, "ignored term name", amount,
                null, null, null, null, "1", "KRW");
    }

    static OpenDartPeriodWitnessResponse witness(String corp, String receipt, String... terms) {
        return new OpenDartPeriodWitnessResponse("000", java.util.Arrays.stream(terms)
                .map(term -> new OpenDartPeriodWitnessResponse.Row(corp, "2025", "11011", receipt, "CFS", term)).toList());
    }

    static OpenDartFinancialResponse values(String corp, String receipt, String revenue, String income) {
        return new OpenDartFinancialResponse("000", "OK", List.of(
                value(corp, receipt, "ifrs_Revenue", revenue),
                value(corp, receipt, "dart_OperatingIncomeLoss", income)));
    }
}
