package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.net.ssl.SSLSession;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

class StockReportSectionTests {
    static final StockCompanyLookup.Company SAMSUNG = new StockCompanyLookup.Company(UUID.randomUUID(), "삼성전자", "00126380");
    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);

    StockPageService service(DartStockClient dart) {
        return new StockPageService(mock(StockCompanyLookup.class), mock(StockPriceStore.class), mock(FinancialHistoryStore.class), dart,
                mock(CompanyNewsService.class), mock(ProfileStore.class), mock(JdbcTemplate.class), JsonMapper.builder().build(), CLOCK);
    }

    @Test
    void sectionUsesWhitelistFallsBackToEarlierYearAndCaches() {
        DartStockClient dart = mock(DartStockClient.class);
        when(dart.reportSection(eq("accnutAdtorNmNdAdtOpinion"), eq("00126380"), eq(2025))).thenReturn(List.of());
        when(dart.reportSection(eq("accnutAdtorNmNdAdtOpinion"), eq("00126380"), eq(2024)))
                .thenReturn(List.of(Map.of("adtor", "삼정회계법인", "adt_opinion", "적정의견", "rcept_no", "20250311001085")));
        var service = service(dart);
        var out = service.reportSection(SAMSUNG, "audit");
        assertEquals("AVAILABLE", out.get("state"));
        assertEquals(2024, out.get("year"));
        assertTrue(String.valueOf(out.get("dartUrl")).endsWith("20250311001085"));
        service.reportSection(SAMSUNG, "audit");
        verify(dart, times(1)).reportSection(eq("accnutAdtorNmNdAdtOpinion"), eq("00126380"), eq(2024));
        assertThrows(IllegalArgumentException.class, () -> service.reportSection(SAMSUNG, "anything"));
    }

    @Test
    void everyScreenSectionIsAllowedByTheClient() {
        for (String api : StockPageService.SECTIONS.values()) assertTrue(api.matches(DartStockClient.REPORT_SECTIONS), api);
    }

    @Test
    void sectionReportsUnavailableWhenDartFails() {
        DartStockClient dart = mock(DartStockClient.class);
        when(dart.reportSection(anyString(), anyString(), anyInt())).thenThrow(new DartStockClient.DartUnavailableException("020"));
        assertEquals("UNAVAILABLE", service(dart).reportSection(SAMSUNG, "treasury").get("state"));
    }

    @Test
    void statementsAreGroupedAndShareOneDownloadWithCashFlow() {
        DartStockClient dart = mock(DartStockClient.class);
        when(dart.fullStatements("00126380", 2025, "CFS")).thenReturn(List.of(
                Map.of("sj_div", "BS", "sj_nm", "재무상태표", "account_nm", "자산총계", "account_detail", "-", "thstrm_amount", "1,000", "frmtrm_amount", "900", "bfefrmtrm_amount", "", "ord", "1", "rcept_no", "20260310002820"),
                Map.of("sj_div", "CF", "sj_nm", "현금흐름표", "account_id", "ifrs-full_CashFlowsFromUsedInOperatingActivities", "account_nm", "영업활동현금흐름", "thstrm_amount", "50", "frmtrm_amount", "40", "bfefrmtrm_amount", "30", "ord", "2", "rcept_no", "20260310002820")));
        var service = service(dart);
        var out = service.statements(SAMSUNG);
        assertEquals("AVAILABLE", out.get("state"));
        assertEquals("CFS", out.get("fsDiv"));
        @SuppressWarnings("unchecked") var groups = (Map<String, List<Map<String, Object>>>) out.get("groups");
        assertEquals(List.of("BS", "CF"), new ArrayList<>(groups.keySet()));
        assertEquals(new BigDecimal("1000"), groups.get("BS").get(0).get("current"));
        assertNull(groups.get("BS").get(0).get("detail"));
        assertNull(groups.get("BS").get(0).get("beforePrevious"));
        service.cashFlow(SAMSUNG);
        verify(dart, times(1)).fullStatements("00126380", 2025, "CFS");
    }

    @Test
    void filingsAddPeriodicMaterialAndExchangeFilingsBeyondTheLatestThreeHundred() {
        DartStockClient dart = mock(DartStockClient.class);
        when(dart.filings(eq("00126380"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(Map.of("rcept_no", "20261010000001", "rcept_dt", "20261010", "report_nm", "임원ㆍ주요주주특정증권등소유상황보고서")));
        when(dart.filingsOfType(eq("00126380"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq("A")))
                .thenReturn(List.of(Map.of("rcept_no", "20260310002820", "rcept_dt", "20260310", "report_nm", "사업보고서 (2025.12)")));
        when(dart.filingsOfType(eq("00126380"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq("B")))
                .thenThrow(new DartStockClient.DartUnavailableException("020"));
        when(dart.filingsOfType(eq("00126380"), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), eq("I")))
                .thenReturn(List.of(Map.of("rcept_no", "20251014800001", "rcept_dt", "20251014", "report_nm", "연결재무제표기준영업(잠정)실적(공정공시)"),
                        Map.of("rcept_no", "20261010000001", "rcept_dt", "20261010", "report_nm", "중복")));
        var out = service(dart).filings(SAMSUNG);
        @SuppressWarnings("unchecked") var items = (List<Map<String, Object>>) out.get("items");
        assertEquals(List.of("20261010000001", "20260310002820", "20251014800001"), items.stream().map(i -> i.get("receiptNo")).toList());
        assertEquals("AVAILABLE", out.get("state"));
    }

    @Test
    void naverNewsComesFirstKeepsTitlesWithTheCompanyAndFallsBackToGdelt() {
        String naver = "{\"items\":["
                + "{\"title\":\"<b>삼성전자</b> 3분기 잠정실적\",\"originallink\":\"https://www.news.example/a\",\"link\":\"https://n.news.naver.com/a\",\"description\":\"영업이익 &quot;증가&quot;\",\"pubDate\":\"Mon, 12 Oct 2026 09:10:00 +0900\"},"
                + "{\"title\":\"<b>삼성전자</b> 3분기 잠정실적\",\"originallink\":\"https://other.example/a\",\"link\":\"\",\"description\":\"중복\",\"pubDate\":\"Mon, 12 Oct 2026 09:11:00 +0900\"},"
                + "{\"title\":\"반도체 업계 동향\",\"originallink\":\"https://news.example/b\",\"link\":\"\",\"description\":\"<b>삼성전자</b> 언급\",\"pubDate\":\"Mon, 12 Oct 2026 08:00:00 +0900\"}]}";
        List<Map<String, String>> headers = new ArrayList<>();
        List<URI> gdeltCalls = new ArrayList<>();
        var news = new CompanyNewsService(uri -> { gdeltCalls.add(uri); return response(200, "{\"articles\":[]}"); },
                (uri, h) -> { headers.add(h); return response(200, naver); }, "ID", "SECRET", JsonMapper.builder().build(), CLOCK);
        var result = news.latest("삼성전자");
        assertEquals("AVAILABLE", result.state());
        assertEquals(CompanyNewsService.NAVER, result.provider());
        assertEquals(2, result.items().size(), "duplicate title removed; loose match kept because fewer than five");
        assertEquals("news.example", result.items().get(0).domain());
        assertEquals("영업이익 \"증가\"", result.items().get(0).summary());
        assertEquals("ID", headers.get(0).get("X-NCP-APIGW-API-KEY-ID"));
        assertTrue(gdeltCalls.isEmpty());

        var failing = new CompanyNewsService(uri -> { gdeltCalls.add(uri); return response(429, "slow down"); },
                (uri, h) -> response(401, "{}"), "ID", "SECRET", JsonMapper.builder().build(), CLOCK);
        assertEquals("UNAVAILABLE", failing.latest("삼성전자").state());
        assertEquals(1, gdeltCalls.size(), "falls back to GDELT when Naver refuses");
        assertTrue(!new CompanyNewsService(uri -> response(200, "{}"), JsonMapper.builder().build(), CLOCK).naverConfigured());
    }

    static HttpResponse<String> response(int status, String body) {
        return new HttpResponse<>() {
            public int statusCode() { return status; }
            public HttpRequest request() { return null; }
            public Optional<HttpResponse<String>> previousResponse() { return Optional.empty(); }
            public HttpHeaders headers() { return HttpHeaders.of(Map.of(), (a, b) -> true); }
            public String body() { return body; }
            public Optional<SSLSession> sslSession() { return Optional.empty(); }
            public URI uri() { return URI.create("https://example.com"); }
            public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
        };
    }
}
