package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.assist.OfficialFactReader.Company;
import com.aira.api.assist.OfficialFactReader.Fact;
import com.aira.api.assist.OfficialFactReader.Resolved;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClaimCheckServiceTests {
    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();
    private static final UUID USER = UUID.randomUUID();
    private static final UUID SAMSUNG = UUID.randomUUID();
    private static final UUID BANK = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

    private static final class FakeReader implements OfficialFactReader {
        public Resolved resolve(String name) {
            if (name.equals("삼성전자")) return new Resolved("ONE", new Company(SAMSUNG, "삼성전자"));
            if (name.equals("중복상사")) return new Resolved("MANY", null);
            if (name.equals("KB금융")) return new Resolved("ONE", new Company(BANK, "KB금융"));
            if (name.equals("iM금융지주")) return new Resolved("LISTED", null);
            return new Resolved("NONE", null);
        }
        public Fact find(UUID id, String predicate, Integer year) {
            if (id.equals(BANK)) {
                // 금융회사: 매출 줄이 없고 순이익만 있다.
                if (!predicate.equals("NET_INCOME")) return null;
                return new Fact(new BigDecimal("5782000000000"), "KRW", LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31),
                        "20260315000111", "https://dart.fss.or.kr/kb");
            }
            if (!id.equals(SAMSUNG)) return null;
            int y = year == null ? 2025 : year;
            if (y != 2025 && y != 2024) return null;
            BigDecimal value = switch (predicate) {
                case "REVENUE" -> y == 2025 ? new BigDecimal("333605938000000") : new BigDecimal("300870903000000");
                case "OPERATING_INCOME" -> y == 2025 ? new BigDecimal("43601051000000") : new BigDecimal("32725961000000");
                case "NET_INCOME" -> y == 2025 ? new BigDecimal("-1200000000000") : new BigDecimal("3000000000000");
                default -> null;
            };
            if (value == null) return null;
            String receipt = y == 2025 ? "20260310002820" : "20250311001085";
            return new Fact(value, "KRW", LocalDate.of(y, 1, 1), LocalDate.of(y, 12, 31), receipt, "https://dart.fss.or.kr/x" + y);
        }
        public Integer latestYear(UUID id) { return id.equals(SAMSUNG) || id.equals(BANK) ? 2025 : null; }
    }

    private static final class FakeModel implements ReadingAssistModel {
        final String answer;
        final boolean fail;
        int calls;
        FakeModel(String answer, boolean fail) { this.answer = answer; this.fail = fail; }
        public boolean available() { return true; }
        public String providerKey() { return "FAKE"; }
        public String modelKey() { return "fake-1"; }
        public String complete(String prompt) {
            calls++;
            if (fail) throw new IllegalStateException("PROVIDER_FAILURE");
            return answer;
        }
    }

    private static String change(String text, String company, String item, String year, String change, String percent) {
        return "{\"text\":\"" + text + "\",\"kind\":\"FACT\",\"company\":\"" + company + "\",\"item\":\"" + item
                + "\",\"year\":" + year + ",\"amount\":\"\",\"unit\":\"\",\"change\":\"" + change + "\",\"percent\":\"" + percent + "\"}";
    }

    private static String claim(String text, String kind, String company, String item, String year, String amount, String unit) {
        return "{\"text\":\"" + text + "\",\"kind\":\"" + kind + "\",\"company\":\"" + company + "\",\"item\":\"" + item
                + "\",\"year\":" + year + ",\"amount\":\"" + amount + "\",\"unit\":\"" + unit + "\"}";
    }

    private static ClaimCheckService service(FakeModel model) {
        return new ClaimCheckService(new FakeReader(), model, CLOCK, MAPPER);
    }


    private static String wrap(String... claims) { return "{\"claims\":[" + String.join(",", claims) + "]}"; }

    private static ClaimDtos.ClaimResult one(String answer) {
        var response = service(new FakeModel(answer, false)).check(USER, "삼성전자 작년 영업이익이 43.6조");
        assertEquals(1, response.results().size());
        return response.results().get(0);
    }

    @Test
    void matchingAmountIsConfirmedWithSource() {
        var result = one(wrap(claim("삼성전자 영업이익 43.6조", "FACT", "삼성전자", "OPERATING_INCOME", "2025", "43.6", "조원")));
        assertEquals("MATCH", result.verdict());
        assertEquals("20260310002820", result.receiptNumber());
        assertEquals("2025-01-01 ~ 2025-12-31", result.period());
        assertTrue(result.official().contains("43,601,051,000,000"));
    }

    @Test
    void roundedDownIsApproximate() {
        assertEquals("APPROX", one(wrap(claim("영업이익 43조", "FACT", "삼성전자", "OPERATING_INCOME", "2025", "43", "조원"))).verdict());
    }

    @Test
    void wrongAmountIsMismatchAndShowsOfficialValue() {
        var result = one(wrap(claim("영업이익 50조", "FACT", "삼성전자", "OPERATING_INCOME", "2025", "50", "조원")));
        assertEquals("MISMATCH", result.verdict());
        assertNotNull(result.official());
        assertNotNull(result.evidenceUrl());
    }

    @Test
    void missingYearUsesLatestAndSaysSo() {
        var result = one(wrap(claim("영업이익 43.6조", "FACT", "삼성전자", "OPERATING_INCOME", "null", "43.6", "조원")));
        assertEquals("MATCH", result.verdict());
        assertTrue(result.note().contains("가장 최근"));
    }

    @Test
    void lossWordFlipsTheSign() {
        var result = one(wrap(claim("순손실 1.2조", "FACT", "삼성전자", "NET_INCOME", "2025", "1.2", "조원")));
        assertEquals("MATCH", result.verdict());
        var positive = one(wrap(claim("순이익 1.2조", "FACT", "삼성전자", "NET_INCOME", "2025", "1.2", "조원")));
        assertEquals("MISMATCH", positive.verdict());
    }

    @Test
    void yearWithoutDataIsNoData() {
        var result = one(wrap(claim("2023년 영업이익 6조", "FACT", "삼성전자", "OPERATING_INCOME", "2023", "6", "조원")));
        assertEquals("NO_DATA", result.verdict());
        assertTrue(result.note().contains("2025"));
    }

    @Test
    void unknownOrAmbiguousCompanyIsNoData() {
        assertEquals("NO_DATA", one(wrap(claim("없는회사 매출 1조", "FACT", "없는회사", "REVENUE", "2025", "1", "조원"))).verdict());
        assertEquals("NO_DATA", one(wrap(claim("중복상사 매출 1조", "FACT", "중복상사", "REVENUE", "2025", "1", "조원"))).verdict());
    }

    @Test
    void opinionForecastAndCauseCannotBeChecked() {
        var response = service(new FakeModel(wrap(
                claim("삼성전자는 좋은 회사", "OPINION", "삼성전자", "OTHER", "null", "", ""),
                claim("내년에 오를 것", "FORECAST", "삼성전자", "OTHER", "null", "", ""),
                claim("반도체 때문에 늘었다", "CAUSE", "삼성전자", "OTHER", "null", "", "")), false)).check(USER, "여러 말이 섞인 글입니다");
        assertEquals(3, response.results().size());
        response.results().forEach(r -> assertEquals("NOT_CHECKABLE", r.verdict()));
    }

    @Test
    void invalidFieldsAreNotCheckable() {
        assertEquals("NOT_CHECKABLE", one(wrap(claim("x", "FACT", "삼성전자", "OTHER", "2025", "1", "조원"))).verdict());
        assertEquals("NOT_CHECKABLE", one(wrap(claim("x", "FACT", "삼성전자", "REVENUE", "2025", "약 1", "조원"))).verdict());
        assertEquals("NOT_CHECKABLE", one(wrap(claim("x", "FACT", "삼성전자", "REVENUE", "2025", "1", "달러"))).verdict());
        assertEquals("NOT_CHECKABLE", one(wrap(claim("x", "FACT", "", "REVENUE", "2025", "1", "조원"))).verdict());
    }

    @Test
    void listedCompanyWithoutOfficialFinancialsIsNotCalledMissing() {
        var result = one(wrap(claim("iM금융지주 순이익 5천억", "FACT", "iM금융지주", "NET_INCOME", "2025", "1", "조원")));
        assertEquals("NO_DATA", result.verdict());
        assertEquals(ClaimCheckService.LISTED_ONLY_LINE, result.note());
        var unknown = one(wrap(claim("없는회사 순이익 5천억", "FACT", "없는회사", "NET_INCOME", "2025", "1", "조원")));
        assertEquals("AIRA에서 이 이름의 회사를 찾지 못했습니다.", unknown.note());
    }

    @Test
    void financialCompanyWithoutRevenueLineSaysWhyInsteadOfComparing() {
        var result = one(wrap(claim("KB금융 매출 80조", "FACT", "KB금융", "REVENUE", "2025", "80", "조원")));
        assertEquals("NO_DATA", result.verdict());
        assertEquals(ClaimCheckService.NO_REVENUE_LINE, result.note());
        assertEquals("MATCH", one(wrap(claim("KB금융 순이익 5조7820억", "FACT", "KB금융", "NET_INCOME", "2025", "57820", "억원"))).verdict());
    }

    @Test
    void unsupportedItemOfKnownCompanyIsNoData() {
        assertEquals("NO_DATA", one(wrap(claim("자산 1조", "FACT", "삼성전자", "TOTAL_ASSETS", "2025", "1", "조원"))).verdict());
    }

    @Test
    void malformedAiAnswerFallsBackToRules() {
        var response = service(new FakeModel("죄송합니다", false)).check(USER, "삼성전자 영업이익 43.6조");
        assertEquals("RULE", response.method());
        assertEquals("MATCH", response.results().get(0).verdict());
        assertTrue(service(new FakeModel("죄송합니다", false)).check(USER, "좋은 회사인 것 같아요").results().isEmpty());
        // AI가 실패해도 간단한 문장은 규칙으로 판정한다(같은 공식 값 비교).
        var fallback = service(new FakeModel("", true)).check(USER, "삼성전자 영업이익 43.6조");
        assertEquals("RULE", fallback.method());
        assertEquals("MATCH", fallback.results().get(0).verdict());
        assertEquals("NONE", service(new FakeModel("", true)).check(USER, "오늘 시장 분위기가 좋네요").method());
    }

    @Test
    void writtenKoreanAmountWinsOverTheAiCopy() {
        // AI가 "1조544억"을 1,544억으로 옮겨 써도 서버가 글에서 직접 읽은 값으로 비교한다.
        String answer = wrap(claim("삼성전자 2025년 영업이익 43조6010억원", "FACT", "삼성전자", "OPERATING_INCOME", "2025", "6010", "억원"));
        var response = service(new FakeModel(answer, false)).check(USER, "삼성전자 2025년 영업이익 43조6010억원");
        // 43조6010억 vs 공식 43조6010.51억: 억 아래를 버린 값이라 「거의 일치」
        assertEquals("APPROX", response.results().get(0).verdict());
        assertEquals("436010억원", response.results().get(0).stated());
    }

    @Test
    void aiVerdictFieldsAreIgnored() {
        String answer = "{\"claims\":[{\"text\":\"영업이익 50조\",\"kind\":\"FACT\",\"company\":\"삼성전자\",\"item\":\"OPERATING_INCOME\",\"year\":2025,"
                + "\"amount\":\"50\",\"unit\":\"조원\",\"verdict\":\"MATCH\",\"true\":true}]}";
        assertEquals("MISMATCH", one(answer).verdict());
    }

    @Test
    void capsClaimsAndRateLimit() {
        String c = claim("영업이익 43.6조", "FACT", "삼성전자", "OPERATING_INCOME", "2025", "43.6", "조원");
        var response = service(new FakeModel(wrap(c, c, c, c, c, c, c), false)).check(USER, "삼성전자 영업이익 43.6조");
        assertEquals(ClaimCheckService.MAX_CLAIMS, response.results().size());
        FakeModel model = new FakeModel(wrap(c), false);
        ClaimCheckService limited = service(model);
        for (int i = 0; i < ClaimCheckService.MAX_CALLS_PER_HOUR; i++) assertEquals("AI", limited.check(USER, "삼성전자 영업이익 43.6조").method());
        assertEquals("NONE", limited.check(USER, "삼성전자 영업이익 43.6조").method());
        assertEquals(ClaimCheckService.MAX_CALLS_PER_HOUR, model.calls);
    }

    @Test
    void rejectsTooShortOrTooLongText() {
        ClaimCheckService s = service(new FakeModel(wrap(), false));
        assertThrows(IllegalArgumentException.class, () -> s.check(USER, "ab"));
        assertThrows(IllegalArgumentException.class, () -> s.check(USER, "가".repeat(1300)));
    }

    @Test
    void unavailableModelFallsBackToRulesForSimpleSentences() {
        var none = new ClaimCheckService(new FakeReader(), null, CLOCK, MAPPER).check(USER, "요즘 반도체가 대세라던데");
        assertEquals("NONE", none.method());
        assertEquals(List.of(), none.results());
        var rule = new ClaimCheckService(new FakeReader(), null, CLOCK, MAPPER)
                .check(USER, "삼성전자 작년 매출이 400조 원을 넘었다던데? 삼성전자는 2024년 영업이익이 32.7조원. 삼성전자 영업이익 33% 늘었다");
        assertEquals("RULE", rule.method());
        assertEquals(2, rule.results().size(), "the percent sentence is not an amount claim");
        assertEquals("MISMATCH", rule.results().get(0).verdict());
        assertEquals("매출", rule.results().get(0).item());
        assertEquals("MATCH", rule.results().get(1).verdict());
        assertEquals(2024, rule.results().get(1).year());
    }

    @Test
    void percentChangeIsComputedFromTwoStoredYears() {
        // 32,725,961 -> 43,601,051 is +33.2%
        var match = one(wrap(change("영업이익 33% 증가", "삼성전자", "OPERATING_INCOME", "2025", "UP", "33")));
        assertEquals("MATCH", match.verdict());
        assertTrue(match.official().contains("2024"));
        assertEquals("20250311001085", match.priorReceiptNumber());
        assertEquals("20260310002820", match.receiptNumber());
        assertEquals("MISMATCH", one(wrap(change("영업이익 50% 증가", "삼성전자", "OPERATING_INCOME", "2025", "UP", "50"))).verdict());
        assertEquals("APPROX", one(wrap(change("영업이익 34% 증가", "삼성전자", "OPERATING_INCOME", "2025", "UP", "34"))).verdict());
    }

    @Test
    void directionOnlyChange() {
        assertEquals("MATCH", one(wrap(change("매출 증가", "삼성전자", "REVENUE", "null", "UP", ""))).verdict());
        assertEquals("MISMATCH", one(wrap(change("매출 감소", "삼성전자", "REVENUE", "2025", "DOWN", ""))).verdict());
        assertEquals("MISMATCH", one(wrap(change("영업이익 10% 감소", "삼성전자", "OPERATING_INCOME", "2025", "DOWN", "10"))).verdict());
    }

    @Test
    void turnToLossAndProfit() {
        assertEquals("MATCH", one(wrap(change("순이익 적자 전환", "삼성전자", "NET_INCOME", "2025", "TURN_LOSS", ""))).verdict());
        assertEquals("MISMATCH", one(wrap(change("순이익 흑자 전환", "삼성전자", "NET_INCOME", "2025", "TURN_PROFIT", ""))).verdict());
        assertEquals("NOT_CHECKABLE", one(wrap(change("매출 적자 전환", "삼성전자", "REVENUE", "2025", "TURN_LOSS", ""))).verdict());
    }

    @Test
    void changeWithoutPreviousYearIsNoData() {
        var result = one(wrap(change("영업이익 10% 증가", "삼성전자", "OPERATING_INCOME", "2024", "UP", "10")));
        assertEquals("NO_DATA", result.verdict());
        assertTrue(result.note().contains("2023"));
    }

    @Test
    void percentOfNegativePreviousValueIsNotComputed() {
        // NET_INCOME 2025 is negative, so a 2026 comparison cannot exist; use 2025 vs 2024 with a positive previous value and DOWN
        var result = one(wrap(change("순이익 140% 감소", "삼성전자", "NET_INCOME", "2025", "DOWN", "140")));
        assertEquals("MATCH", result.verdict());
    }
}
