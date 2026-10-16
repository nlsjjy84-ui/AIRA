package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class StockQuarterTests {
    @Test
    void cumulativeRowsUseYearToDateAmountsAndConsolidatedStatements() {
        var rows = FinancialHistoryRows.cumulative(List.of(
                Map.of("corp_code", "00126380", "fs_div", "CFS", "account_nm", "매출액", "rcept_no", "20260814000001", "thstrm_amount", "80", "thstrm_add_amount", "150"),
                Map.of("corp_code", "00126380", "fs_div", "OFS", "account_nm", "매출액", "rcept_no", "20260814000001", "thstrm_amount", "1", "thstrm_add_amount", "1"),
                Map.of("corp_code", "00126380", "fs_div", "CFS", "account_nm", "자산총계", "rcept_no", "20260814000001", "thstrm_amount", "999")), 2026, "H1");
        assertEquals(2, rows.size());
        assertEquals(new BigDecimal("150"), rows.get(0).amount());
        // 재무상태표 항목은 누적이 아니라 분기 말 잔액(thstrm_amount)을 저장한다.
        assertEquals("TOTAL_ASSETS", rows.get(1).account());
        assertEquals(new BigDecimal("999"), rows.get(1).amount());
        var q1 = FinancialHistoryRows.cumulative(List.of(
                Map.of("corp_code", "00126380", "fs_div", "CFS", "account_nm", "영업이익", "rcept_no", "20260515000001", "thstrm_amount", "7")), 2026, "Q1");
        assertEquals(new BigDecimal("7"), q1.get(0).amount());
        assertTrue(FinancialHistoryRows.cumulative(List.of(
                Map.of("corp_code", "00126380", "fs_div", "CFS", "account_nm", "영업이익", "rcept_no", "1", "thstrm_amount", "7")), 2026, "Q3").isEmpty());
    }

    @Test
    void quartersAreDifferencesOfYearToDateValuesAndTheFourthUsesTheAnnualReport() {
        FinancialHistoryStore store = mock(FinancialHistoryStore.class);
        when(store.quartersFor("00126380")).thenReturn(List.of(
                new FinancialHistoryRows.Cumulative("00126380", 2025, "Q1", "REVENUE", new BigDecimal("70"), "r1"),
                new FinancialHistoryRows.Cumulative("00126380", 2025, "H1", "REVENUE", new BigDecimal("150"), "r2"),
                new FinancialHistoryRows.Cumulative("00126380", 2025, "Q3", "REVENUE", new BigDecimal("240"), "r3"),
                new FinancialHistoryRows.Cumulative("00126380", 2026, "Q1", "REVENUE", new BigDecimal("90"), "r4")));
        when(store.forCompany("00126380")).thenReturn(List.of(new FinancialHistoryRows.Value("00126380", 2025, "REVENUE", new BigDecimal("330"), "CFS", "r5", 2025)));
        var service = new StockPageService(mock(StockCompanyLookup.class), mock(StockPriceStore.class), store, mock(DartStockClient.class),
                mock(CompanyNewsService.class), mock(ProfileStore.class), mock(JdbcTemplate.class), JsonMapper.builder().build());
        var out = service.quarters(new StockCompanyLookup.Company(UUID.randomUUID(), "삼성전자", "00126380"));
        @SuppressWarnings("unchecked") var items = (List<Map<String, Object>>) out.get("items");
        assertEquals(List.of("2025 1분기", "2025 2분기", "2025 3분기", "2025 4분기", "2026 1분기"), items.stream().map(i -> i.get("label")).toList());
        assertEquals(List.of(new BigDecimal("70"), new BigDecimal("80"), new BigDecimal("90"), new BigDecimal("90"), new BigDecimal("90")),
                items.stream().map(i -> ((Map<?, ?>) i.get("values")).get("REVENUE")).toList());
        assertEquals("r5", items.get(3).get("receiptNo"));
    }
}
