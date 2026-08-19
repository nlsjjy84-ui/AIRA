package com.aira.api.market.opendart;

import java.util.List;

public record OpenDartFinancialResponse(
        String status,
        String message,
        List<OpenDartFinancialRow> list) {
}
