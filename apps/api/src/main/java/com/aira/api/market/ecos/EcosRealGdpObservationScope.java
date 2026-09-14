package com.aira.api.market.ecos;

import java.util.regex.Pattern;

public record EcosRealGdpObservationScope(
        String startTime,
        String endTime,
        EcosObservationBudget budget) {
    private static final Pattern QUARTER = Pattern.compile("[0-9]{4}Q[1-4]");

    public EcosRealGdpObservationScope {
        validateQuarter(startTime, "startTime");
        validateQuarter(endTime, "endTime");
        if (quarterOrdinal(startTime) > quarterOrdinal(endTime)) {
            throw new IllegalArgumentException("startTime must not be after endTime");
        }
        if (budget == null) throw new IllegalArgumentException("budget must not be null");
    }

    public long quarterCount() {
        return quarterOrdinal(endTime) - quarterOrdinal(startTime) + 1L;
    }
    boolean contains(String time) {
        validateQuarter(time, "time");
        long ordinal = quarterOrdinal(time);
        return ordinal >= quarterOrdinal(startTime) && ordinal <= quarterOrdinal(endTime);
    }

    private static void validateQuarter(String value, String name) {
        if (value == null || !QUARTER.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must match YYYYQn");
        }
    }

    private static long quarterOrdinal(String value) {
        long year = Long.parseLong(value.substring(0, 4));
        long quarter = value.charAt(5) - '0';
        return year * 4L + quarter;
    }
}
