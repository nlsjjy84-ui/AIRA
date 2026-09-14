package com.aira.api.market.ecos;

import java.util.regex.Pattern;

record EcosRealGdpSearchRequest(
        long startRow,
        long endRow,
        String startTime,
        String endTime) {
    private static final Pattern QUARTER = Pattern.compile("[0-9]{4}Q[1-4]");

    EcosRealGdpSearchRequest {
        new EcosPageRange(startRow, endRow);
        validateQuarter(startTime, "startTime");
        validateQuarter(endTime, "endTime");
        if (quarterOrdinal(startTime) > quarterOrdinal(endTime)) {
            throw new IllegalArgumentException("startTime must not be after endTime");
        }
    }
    private static void validateQuarter(String value, String name) {
        if (value == null || !QUARTER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match YYYYQn");
        }
    }

    private static int quarterOrdinal(String value) {
        int year = Integer.parseInt(value.substring(0, 4));
        int quarter = value.charAt(5) - '0';
        return year * 4 + quarter;
    }
}
