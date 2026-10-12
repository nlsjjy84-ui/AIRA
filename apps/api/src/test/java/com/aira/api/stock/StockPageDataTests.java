package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

class StockPageDataTests {
    private final ObjectMapper json = JsonMapper.builder().build();

    @Test
    void krxDailyRowsKeepNumbersAndSkipBlankOrBrokenRows() {
        var rows = KrxDailyRows.convert("KOSPI", LocalDate.of(2026, 10, 8), List.of(
                Map.of("ISU_CD", "005930", "TDD_CLSPRC", "262000", "TDD_OPNPRC", "270,000", "FLUC_RT", "-2.42",
                        "ACC_TRDVOL", "21000000", "MKTCAP", "1564000000000000", "LIST_SHRS", "5919637922"),
                Map.of("ISU_CD", "000660", "TDD_CLSPRC", "-"),
                Map.of("ISU_CD", "BAD", "TDD_CLSPRC", "100")));
        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("262000"), rows.get(0).close());
        assertEquals(new BigDecimal("270000"), rows.get(0).open());
        assertEquals(new BigDecimal("-2.42"), rows.get(0).changeRate());
        assertEquals(21000000L, rows.get(0).volume());
        assertNull(rows.get(0).high());
    }

    @Test
    void krxIndexRowsKeepOnlyTheMainIndex() {
        var rows = KrxDailyRows.convertIndex("KOSPI", "IDX_KOSPI", LocalDate.of(2026, 10, 8), List.of(
                Map.of("IDX_NM", "코스피 200", "CLSPRC_IDX", "900.10"),
                Map.of("IDX_NM", "코스피", "CLSPRC_IDX", "6625.93", "FLUC_RT", "-2.62")));
        assertEquals(1, rows.size());
        assertEquals("KOSPI", rows.get(0).shortCode());
        assertEquals("IDX_KOSPI", rows.get(0).market());
        assertEquals(new BigDecimal("6625.93"), rows.get(0).close());
    }

    @Test
    void financialRowsPreferConsolidatedAndSpreadThreeYears() {
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(row("00126380", "CFS", "매출액", "2025", "333,605,938,000,000", "300,870,903,000,000", "258,935,494,000,000"));
        rows.add(row("00126380", "OFS", "매출액", "2025", "1", "1", "1"));
        rows.add(row("00126380", "CFS", "영업이익", "2025", "43,601,051,000,000", "32,725,961,000,000", "-"));
        rows.add(row("00999999", "OFS", "당기순이익(손실)", "2025", "-5,000", "1,000", ""));
        rows.add(row("00126380", "CFS", "법인세차감전 순이익", "2025", "9", "9", "9"));
        var values = FinancialHistoryRows.convert(rows);
        assertTrue(values.stream().noneMatch(v -> v.amount().equals(BigDecimal.ONE)), "OFS must be ignored when CFS exists");
        assertEquals(new BigDecimal("258935494000000"), find(values, "00126380", 2023, "REVENUE"));
        assertEquals(new BigDecimal("32725961000000"), find(values, "00126380", 2024, "OPERATING_INCOME"));
        assertNull(find(values, "00126380", 2023, "OPERATING_INCOME"));
        assertEquals(new BigDecimal("-5000"), find(values, "00999999", 2025, "NET_INCOME"));
        assertEquals(7, values.size());
    }

    @Test
    void financialRowsFallBackToTheRequestedYear() {
        var values = FinancialHistoryRows.convert(List.of(Map.of("corp_code", "00126380", "fs_div", "CFS", "account_nm", "매출액",
                "rcept_no", "20260310002820", "thstrm_amount", "10", "frmtrm_amount", "9", "bfefrmtrm_amount", "8")), 2025);
        assertEquals(new BigDecimal("8"), find(values, "00126380", 2023, "REVENUE"));
        assertTrue(FinancialHistoryRows.convert(List.of(Map.of("corp_code", "00126380", "fs_div", "CFS", "account_nm", "매출액",
                "rcept_no", "1", "thstrm_amount", "10"))).isEmpty());
    }

    @Test
    void filingCategoriesGroupCommonTitles() {
        assertEquals("PERIODIC", FilingCategories.of("사업보고서 (2025.12)"));
        assertEquals("EARNINGS", FilingCategories.of("연결재무제표기준영업(잠정)실적(공정공시)"));
        assertEquals("OWNERSHIP", FilingCategories.of("임원ㆍ주요주주특정증권등소유상황보고서"));
        assertEquals("MATERIAL", FilingCategories.of("주요사항보고서(자기주식취득결정)"));
        assertEquals("MATERIAL", FilingCategories.of("현금ㆍ현물배당결정"));
        assertEquals("OTHER", FilingCategories.of("기업설명회(IR)개최(안내공시)"));
    }

    @Test
    void dartClientTreatsNoDataAsEmptyAndOtherStatusesAsFailure() {
        var empty = new DartStockClient(uri -> "{\"status\":\"013\",\"message\":\"조회된 데이타가 없습니다.\"}", json, "KEY");
        assertTrue(empty.multiAccounts(List.of("00126380"), 2025).isEmpty());
        var limited = new DartStockClient(uri -> "{\"status\":\"020\"}", json, "KEY");
        assertThrows(DartStockClient.DartUnavailableException.class, () -> limited.filings("00126380",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 10, 1)));
        var noKey = new DartStockClient(uri -> "{}", json, "");
        assertThrows(DartStockClient.DartUnavailableException.class, () -> noKey.company("00126380"));
        List<URI> seen = new ArrayList<>();
        var ok = new DartStockClient(uri -> { seen.add(uri); return "{\"status\":\"000\",\"total_page\":1,\"list\":[{\"rcept_no\":\"20260310002820\",\"report_nm\":\"사업보고서\"}]}"; }, json, "KEY");
        assertEquals("사업보고서", ok.filings("00126380", LocalDate.of(2025, 10, 1), LocalDate.of(2026, 10, 1)).get(0).get("report_nm"));
        assertTrue(seen.get(0).toString().contains("bgn_de=20251001"));
    }

    @Test
    void companyNewsKeepsGdeltGapAndCachesPerCompany() {
        List<URI> calls = new ArrayList<>();
        String body = "{\"articles\":[{\"title\":\"삼성전자 &amp; 실적\",\"url\":\"https://news.example.com/a\",\"domain\":\"news.example.com\",\"seendate\":\"20261010T010000Z\"},"
                + "{\"title\":\"링크 없음\",\"url\":\"ftp://x\",\"seendate\":\"20261010T010000Z\"}]}";
        var clock = new MutableClock(Instant.parse("2026-10-12T00:00:00Z"));
        var service = new CompanyNewsService(uri -> { calls.add(uri); return response(200, body); }, json, clock);
        var first = service.latest("삼성전자");
        assertEquals("AVAILABLE", first.state());
        assertEquals(1, first.items().size());
        assertEquals("삼성전자 & 실적", first.items().get(0).title());
        assertEquals("AVAILABLE", service.latest("삼성전자").state());
        assertEquals("BUSY", service.latest("NAVER").state(), "second company within 6 seconds waits");
        clock.now = clock.now.plusSeconds(7);
        assertEquals("AVAILABLE", service.latest("NAVER").state());
        assertEquals(2, calls.size());
        assertTrue(calls.get(0).toString().contains("sourcelang%3Akorean"));
    }

    @Test
    void companyNewsFallsBackToUnavailableOnRateLimit() {
        var service = new CompanyNewsService(uri -> response(429, "Please limit requests"), json,
                Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC));
        assertEquals("UNAVAILABLE", service.latest("삼성전자").state());
    }

    @Test
    void marketFromCorpClass() {
        assertEquals("KOSPI", StockPageService.market(Map.of("corp_cls", "Y")));
        assertEquals("KOSDAQ", StockPageService.market(Map.of("corp_cls", "K")));
        assertNull(StockPageService.market(Map.of()));
        assertEquals(new BigDecimal("13.07"), StockPageService.ratio(new BigDecimal("43601"), new BigDecimal("333605")));
        assertNull(StockPageService.ratio(BigDecimal.ONE, BigDecimal.ZERO));
    }

    private static Map<String, String> row(String corp, String fs, String account, String year, String th, String fr, String bf) {
        return Map.of("corp_code", corp, "fs_div", fs, "account_nm", account, "bsns_year", year, "rcept_no", "20260310002820",
                "thstrm_amount", th, "frmtrm_amount", fr, "bfefrmtrm_amount", bf);
    }

    private static BigDecimal find(List<FinancialHistoryRows.Value> values, String corp, int year, String account) {
        return values.stream().filter(v -> v.corpCode().equals(corp) && v.fiscalYear() == year && v.account().equals(account))
                .map(FinancialHistoryRows.Value::amount).findFirst().orElse(null);
    }

    static final class MutableClock extends Clock {
        Instant now;
        MutableClock(Instant now) { this.now = now; }
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    static HttpResponse<String> response(int status, String body) {
        return new HttpResponse<>() {
            public int statusCode() { return status; }
            public HttpRequest request() { return null; }
            public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            public String body() { return body; }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return URI.create("https://api.gdeltproject.org"); }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
