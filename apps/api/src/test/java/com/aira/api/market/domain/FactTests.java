package com.aira.api.market.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aira.api.market.normalization.EarningsFactDedupKey;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FactTests {
    @Test
    void genericSupportedNumberAllowsNoEvent() throws Exception {
        MarketEntity subject = newInstance(MarketEntity.class);
        set(subject, "id", UUID.randomUUID());
        set(subject, "canonicalKey", "COMPANY:SAMSUNG-ELECTRONICS");
        LocalDate periodStart = LocalDate.of(2026, 4, 1);
        LocalDate periodEnd = LocalDate.of(2026, 6, 30);
        byte[] key = EarningsFactDedupKey.create(FactPredicate.REVENUE,
                subject.getCanonicalKey(), periodStart, periodEnd, "KRW");

        Fact fact = Fact.supportedNumber(subject, null, FactPredicate.REVENUE,
                new BigDecimal("100.00"), "KRW", periodStart, periodEnd, key,
                OffsetDateTime.parse("2026-07-31T00:00:00Z"));

        assertNull(fact.getEvent());
        assertEquals(FactStatus.SUPPORTED, fact.getStatus());
    }

    private static <T> T newInstance(Class<T> type) throws Exception {
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
