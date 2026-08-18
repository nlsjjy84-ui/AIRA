package com.aira.api.market.normalization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.aira.api.market.domain.FactPredicate;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EarningsFactDedupKeyTests {
    private static final LocalDate PERIOD_START = LocalDate.of(2026, 4, 1);
    private static final LocalDate PERIOD_END = LocalDate.of(2026, 6, 30);

    @Test
    void keyIsDeterministicAndSha256Length() {
        byte[] first = key(FactPredicate.REVENUE, PERIOD_START, PERIOD_END, "KRW");
        byte[] second = key(FactPredicate.REVENUE, PERIOD_START, PERIOD_END, "KRW");

        assertArrayEquals(first, second);
        assertEquals(32, first.length);
    }

    @Test
    void predicatePeriodAndCurrencyAreIdentityComponents() {
        byte[] baseline = key(FactPredicate.REVENUE, PERIOD_START, PERIOD_END, "KRW");

        assertFalse(Arrays.equals(baseline,
                key(FactPredicate.OPERATING_INCOME, PERIOD_START, PERIOD_END, "KRW")));
        assertFalse(Arrays.equals(baseline,
                key(FactPredicate.REVENUE, PERIOD_START.minusMonths(3), PERIOD_END, "KRW")));
        assertFalse(Arrays.equals(baseline,
                key(FactPredicate.REVENUE, PERIOD_START, PERIOD_END, "USD")));
    }

    @Test
    void assertedValueIsNotAnIdentityComponent() {
        byte[] forFirstValue = key(FactPredicate.REVENUE, PERIOD_START, PERIOD_END, "KRW");
        byte[] forDifferentValue = key(FactPredicate.REVENUE, PERIOD_START, PERIOD_END, "KRW");

        assertArrayEquals(forFirstValue, forDifferentValue);
    }

    private static byte[] key(FactPredicate predicate, LocalDate start, LocalDate end,
            String currencyCode) {
        return EarningsFactDedupKey.create(predicate, "COMPANY:SAMSUNG-ELECTRONICS",
                start, end, currencyCode);
    }
}
