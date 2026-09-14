package com.aira.api.market.domain;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.aira.api.market.normalization.StatisticalFactDedupKey;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticalFactModelTests {
    @Mock MarketEntity country;
    @Mock MarketEntity company;
    @Mock StatisticalSeries series;

    @Test
    void realGdpUsesProviderNeutralNumberShapeAndDeterministicDedup() {
        UUID countryId = UUID.randomUUID();
        when(country.getId()).thenReturn(countryId);
        when(country.getEntityType()).thenReturn(EntityType.COUNTRY);
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        byte[] one = key(start, end);
        byte[] two = key(start, end);

        Fact fact = Fact.supportedStatisticalNumber(
                country, FactPredicate.REAL_GDP, new BigDecimal("596692.8"),
                start, end, one, OffsetDateTime.parse("2026-09-12T00:00:00Z"));

        assertArrayEquals(one, two);
        assertEquals(FactPredicate.REAL_GDP, fact.getPredicate());
        assertEquals(FactStatus.SUPPORTED, fact.getStatus());
        assertEquals(FactValueType.NUMBER, fact.getValueType());
        assertEquals(new BigDecimal("596692.8"), fact.getValueNumber());
        assertNull(fact.getCurrencyCode());
        assertNull(fact.getEvent());
        assertEquals(start, fact.getPeriodStart());
        assertEquals(end, fact.getPeriodEnd());
    }

    @Test
    void statisticalFactoryRejectsCompanyAndNonQuarterAndEarningsFactoryRejectsRealGdp() {
        when(country.getId()).thenReturn(UUID.randomUUID());
        when(country.getEntityType()).thenReturn(EntityType.COUNTRY);
        when(company.getId()).thenReturn(UUID.randomUUID());
        when(company.getEntityType()).thenReturn(EntityType.COMPANY);
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        byte[] key = key(start, end);

        assertThrows(IllegalArgumentException.class,
                () -> Fact.supportedStatisticalNumber(
                        company, FactPredicate.REAL_GDP, BigDecimal.ONE,
                        start, end, key, OffsetDateTime.now()));
        assertThrows(IllegalArgumentException.class,
                () -> Fact.supportedStatisticalNumber(
                        country, FactPredicate.REAL_GDP, BigDecimal.ONE,
                        start.plusDays(1), end, key, OffsetDateTime.now()));
        assertThrows(IllegalArgumentException.class,
                () -> Fact.supportedNumber(
                        country, null, FactPredicate.REAL_GDP, BigDecimal.ONE, "KRW",
                        start, end, key, OffsetDateTime.now()));
    }

    @Test
    void statisticalContextRequiresExactSeriesSemanticsAndSameSubject() throws Exception {
        when(country.getId()).thenReturn(UUID.randomUUID());
        when(country.getEntityType()).thenReturn(EntityType.COUNTRY);
        LocalDate start = LocalDate.of(2026, 4, 1);
        LocalDate end = LocalDate.of(2026, 6, 30);
        Fact fact = Fact.supportedStatisticalNumber(country, FactPredicate.REAL_GDP,
                BigDecimal.ONE, start, end, key(start, end), OffsetDateTime.now());
        set(fact, "id", UUID.randomUUID());
        when(series.getId()).thenReturn(UUID.randomUUID());
        when(series.isActive()).thenReturn(true);
        when(series.getMetric()).thenReturn(StatisticalMetric.REAL_GDP);
        when(series.getFrequency()).thenReturn(StatisticalFrequency.QUARTERLY);
        when(series.getAdjustment()).thenReturn(StatisticalAdjustment.SEASONALLY_ADJUSTED);
        when(series.getValueKind()).thenReturn(StatisticalValueKind.LEVEL);
        when(series.getSubjectEntity()).thenReturn(country);

        FactStatisticalContext context = FactStatisticalContext.verified(
                fact, series, StatisticalUnit.KRW_BILLION, OffsetDateTime.now());
        assertEquals(fact.getId(), context.getFactId());
        assertEquals(StatisticalUnit.KRW_BILLION, context.getCanonicalUnit());

        when(series.getMetric()).thenReturn(null);
        assertThrows(IllegalArgumentException.class,
                () -> FactStatisticalContext.verified(
                        fact, series, StatisticalUnit.KRW_BILLION, OffsetDateTime.now()));
    }

    private byte[] key(LocalDate start, LocalDate end) {
        return StatisticalFactDedupKey.create(
                "COUNTRY:KR", StatisticalMetric.REAL_GDP, StatisticalFrequency.QUARTERLY,
                StatisticalAdjustment.SEASONALLY_ADJUSTED, StatisticalValueKind.LEVEL,
                StatisticalUnit.KRW_BILLION, start, end);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
