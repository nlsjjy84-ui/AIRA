package com.aira.api.market.query;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.dto.CanonicalDataState;
import com.aira.api.market.dto.FinancialExactComparisonResponse.ExactPeriod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FinancialExactComparisonQueryTests {
    UUID company = UUID.randomUUID();
    ExactPeriod a = new ExactPeriod(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31), "20250101000001");
    ExactPeriod b = new ExactPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), "20260101000001");
    FinancialHistoricalExactQuery exact = mock(FinancialHistoricalExactQuery.class);
    FinancialExactComparisonQuery query = new FinancialExactComparisonQuery(exact);

    @Test void computesOnlyBMinusAFromTwoExactVerifiedResults() {
        when(exact.find(company, a.periodStart(), a.periodEnd(), a.receipt(), Set.of("REVENUE")))
                .thenReturn(result(a, "100"));
        when(exact.find(company, b.periodStart(), b.periodEnd(), b.receipt(), Set.of("REVENUE")))
                .thenReturn(result(b, "125"));
        var answer = query.find(company, a, b, Set.of(FactPredicate.REVENUE));
        assertEquals(CanonicalDataState.AVAILABLE, answer.state());
        assertEquals(new BigDecimal("25"), answer.metrics().getFirst().changeAmountBMinusA());
        assertEquals(new BigDecimal("25.0000"), answer.metrics().getFirst().changePercentBOverA());
        assertEquals(a.receipt(), answer.a().receipt());
        assertEquals(b.receipt(), answer.b().receipt());
        assertEquals(1, answer.metrics().getFirst().a().evidenceIds().size());
        verify(exact, times(2)).find(eq(company), any(), any(), any(), eq(Set.of("REVENUE")));
    }

    @Test void zeroBaseKeepsAmountButNeverInventsPercent() {
        when(exact.find(company, a.periodStart(), a.periodEnd(), a.receipt(), Set.of("REVENUE")))
                .thenReturn(result(a, "0"));
        when(exact.find(company, b.periodStart(), b.periodEnd(), b.receipt(), Set.of("REVENUE")))
                .thenReturn(result(b, "100"));
        var metric = query.find(company, a, b, Set.of(FactPredicate.REVENUE)).metrics().getFirst();
        assertEquals(new BigDecimal("100"), metric.changeAmountBMinusA());
        assertNull(metric.changePercentBOverA());
        assertEquals("BASE_NON_POSITIVE", metric.percentReason());
    }

    @Test void missingBBlocksWholeComparisonWithoutFallback() {
        when(exact.find(company, a.periodStart(), a.periodEnd(), a.receipt(), Set.of("REVENUE")))
                .thenReturn(result(a, "100"));
        when(exact.find(company, b.periodStart(), b.periodEnd(), b.receipt(), Set.of("REVENUE")))
                .thenThrow(new CompanyFinancialFactsQueryException(
                        CompanyFinancialFactsQueryException.Category.FACTS_NOT_FOUND, "missing"));
        var answer = query.find(company, a, b, Set.of(FactPredicate.REVENUE));
        assertEquals(CanonicalDataState.NO_DATA, answer.state());
        assertEquals("B_FACTS_NOT_FOUND", answer.reason());
        assertTrue(answer.metrics().isEmpty());
    }

    private CompanyFinancialFactsResult result(ExactPeriod period, String value) {
        return new CompanyFinancialFactsResult(company, List.of(new CompanyFinancialFactView(company,
                FactPredicate.REVENUE, new BigDecimal(value), "KRW", period.periodStart(),
                period.periodEnd(), null, OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                "OpenDART", UUID.randomUUID(), period.receipt(), "https://dart.fss.or.kr")));
    }
}
