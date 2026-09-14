package com.aira.api.market.ecos;

import java.time.LocalDate;

record EcosQuarterPeriod(String providerTime, LocalDate start, LocalDate end) {
    static EcosQuarterPeriod from(String time) {
        if (time == null || !time.matches("[0-9]{4}Q[1-4]")) {
            throw new IllegalArgumentException("ECOS quarter must match YYYYQn");
        }
        int year = Integer.parseInt(time.substring(0, 4));
        int quarter = time.charAt(5) - '0';
        int startMonth = 1 + (quarter - 1) * 3;
        LocalDate start = LocalDate.of(year, startMonth, 1);
        return new EcosQuarterPeriod(time, start, start.plusMonths(3).minusDays(1));
    }

    EcosQuarterPeriod {
        if (providerTime == null || start == null || end == null
                || !end.equals(start.plusMonths(3).minusDays(1))) {
            throw new IllegalArgumentException("Exact quarter period is required");
        }
    }
}
