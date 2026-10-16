package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 사업보고서 표에서 합계 줄을 골라 쓰고, 합계와 세부 줄을 겹쳐 더하지 않는다. */
class StockReportFactSourceTests {
    private final StockPageService pages = mock(StockPageService.class);
    private final UUID id = UUID.randomUUID();
    private final StockCompanyLookup.Company company = new StockCompanyLookup.Company(id, "테스트", "00112233");

    private StockReportFactSource source(Map<String, Object> details) {
        when(pages.company(id)).thenReturn(Optional.of(company));
        when(pages.reportDetails(company)).thenReturn(details);
        return new StockReportFactSource(pages);
    }

    @Test
    void headcountUsesTheTotalRowsInsteadOfAddingTotalsToDetails() {
        var src = source(Map.of("state", "AVAILABLE", "year", 2025, "receiptNo", "20260310000001", "employees", List.of(
                Map.of("fo_bbm", "DX", "sexdstn", "남", "sm", "1,000"),
                Map.of("fo_bbm", "DX", "sexdstn", "여", "sm", "500"),
                Map.of("fo_bbm", "합계", "sexdstn", "남", "sm", "1,000"),
                Map.of("fo_bbm", "합계", "sexdstn", "여", "sm", "500"),
                Map.of("fo_bbm", "합계", "sexdstn", "합계", "sm", "1,500"))));
        assertEquals(0, new BigDecimal("1500").compareTo(src.employees(id, 2025).amount()));
        assertEquals(null, src.employees(id, 2024));
    }

    @Test
    void headcountWithoutTotalRowsAddsTheDetailRows() {
        var src = source(Map.of("state", "AVAILABLE", "year", 2025, "receiptNo", "20260310000001", "employees", List.of(
                Map.of("fo_bbm", "제조", "sexdstn", "남", "sm", "70"), Map.of("fo_bbm", "제조", "sexdstn", "여", "sm", "30"))));
        assertEquals(0, new BigDecimal("100").compareTo(src.employees(id, null).amount()));
    }

    @Test
    void dividendPerShareTakesTheCommonShareCashDividend() {
        var src = source(Map.of("state", "AVAILABLE", "year", 2025, "receiptNo", "20260310000001", "dividends", List.of(
                Map.of("se", "주당액면가액(원)", "stock_knd", "-", "thstrm", "100"),
                Map.of("se", "주당 현금배당금(원)", "stock_knd", "우선주", "thstrm", "1,445"),
                Map.of("se", "주당 현금배당금(원)", "stock_knd", "보통주", "thstrm", "1,444"))));
        assertEquals(0, new BigDecimal("1444").compareTo(src.dividendPerShare(id, 2025).amount()));
    }
}
