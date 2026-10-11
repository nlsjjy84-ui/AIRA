package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.assist.FilingDtos.CompareRequest;
import com.aira.api.assist.FilingDtos.QuoteRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FilingQuoteServiceTests {
    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();
    private static final UUID USER = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);
    private static final String R1 = "20260101000001";
    private static final String R2 = "20260101000002";
    private static final String TEXT = "계약금액 | 1,234억원\n계약상대 | 주식회사 가나다\n계약기간은 2026.01.01부터 2026.12.31까지 입니다.\n배당에 관한 내용은 없습니다 확인하세요.";

    private static final class FakeSource implements FilingTextSource {
        final Map<String, String> texts;
        int fetches;
        FakeSource(Map<String, String> texts) { this.texts = texts; }
        public String fetch(String receipt) {
            fetches++;
            String text = texts.get(receipt);
            if (text == null) throw new IllegalStateException("NOT_FOUND");
            return text;
        }
    }

    private static final class FakeModel implements ReadingAssistModel {
        final String answer;
        final boolean fail;
        FakeModel(String answer, boolean fail) { this.answer = answer; this.fail = fail; }
        public boolean available() { return true; }
        public String providerKey() { return "FAKE"; }
        public String modelKey() { return "fake-1"; }
        public String complete(String prompt) {
            if (fail) throw new IllegalStateException("PROVIDER_FAILURE");
            return answer;
        }
    }

    private static FilingQuoteService service(ReadingAssistModel model, FakeSource source) {
        return new FilingQuoteService(source, model, CLOCK, MAPPER);
    }

    private static FakeSource source() { return new FakeSource(Map.of(R1, TEXT)); }

    @Test
    void keepsAVerifiedQuoteAndAPlainNote() {
        String answer = "{\"quotes\":[{\"p\":1,\"text\":\"계약금액 | 1,234억원\"}],\"explain\":\"계약금액은 1,234억원이라고 적혀 있습니다.\"}";
        var response = service(new FakeModel(answer, false), source()).ask(USER, new QuoteRequest(R1, "계약금액이 얼마야"));
        assertEquals("AI", response.method());
        assertEquals(1, response.quotes().size());
        assertEquals("계약금액 | 1,234억원", response.quotes().get(0).text());
        assertEquals(1, response.quotes().get(0).passageNo());
        assertNotNull(response.explanation());
    }

    @Test
    void dropsAQuoteThatIsNotInTheFilingAndShowsTheRealPassage() {
        String answer = "{\"quotes\":[{\"p\":1,\"text\":\"계약금액 | 9,999억원\"}],\"explain\":\"계약금액은 9,999억원입니다.\"}";
        var response = service(new FakeModel(answer, false), source()).ask(USER, new QuoteRequest(R1, "계약금액이 얼마야"));
        assertEquals("RULE", response.method());
        assertEquals("계약금액 | 1,234억원", response.quotes().get(0).text());
        assertNull(response.explanation());
    }

    @Test
    void rejectsNotesWithNewNumbersCausesOrAdvice() {
        for (String bad : new String[] {"계약금액은 5,000억원입니다.", "계약금액 1,234억원 때문에 좋아 보입니다.", "1,234억원이므로 매수할 만합니다."}) {
            String answer = "{\"quotes\":[{\"p\":1,\"text\":\"계약금액 | 1,234억원\"}],\"explain\":\"" + bad + "\"}";
            var response = service(new FakeModel(answer, false), source()).ask(USER, new QuoteRequest(R1, "계약금액이 얼마야"));
            assertEquals("QUOTES", response.method(), bad);
            assertNull(response.explanation());
            assertEquals(1, response.quotes().size());
        }
    }

    @Test
    void showsOriginalPassagesWhenTheModelFailsOrIsMissing() {
        var failing = service(new FakeModel("", true), source()).ask(USER, new QuoteRequest(R1, "계약금액이 얼마야"));
        assertEquals("RULE", failing.method());
        var missing = service(null, source()).ask(USER, new QuoteRequest(R1, "계약금액이 얼마야"));
        assertEquals("RULE", missing.method());
        assertEquals("계약금액 | 1,234억원", missing.quotes().get(0).text());
    }

    @Test
    void saysWhenNothingMatchesOrTheFilingIsMissing() {
        var nothing = service(null, source()).ask(USER, new QuoteRequest(R1, "유상증자 일정"));
        assertEquals("NONE", nothing.method());
        assertTrue(nothing.quotes().isEmpty());
        var missing = service(null, source()).ask(USER, new QuoteRequest(R2, "계약금액"));
        assertEquals("NONE", missing.method());
    }

    @Test
    void rejectsMalformedInput() {
        var service = service(null, source());
        assertThrows(IllegalArgumentException.class, () -> service.ask(USER, new QuoteRequest("123", "계약금액")));
        assertThrows(IllegalArgumentException.class, () -> service.ask(USER, new QuoteRequest(R1, " ")));
        assertThrows(IllegalArgumentException.class, () -> service.ask(USER, null));
    }

    @Test
    void reusesTheFetchedFilingForAShortTime() {
        var source = source();
        var service = service(null, source);
        service.ask(USER, new QuoteRequest(R1, "계약금액"));
        service.ask(USER, new QuoteRequest(R1, "계약상대"));
        assertEquals(1, source.fetches);
    }

    @Test
    void limitsCallsPerHour() {
        var service = service(null, source());
        for (int i = 0; i < FilingQuoteService.MAX_CALLS_PER_HOUR; i++) service.ask(USER, new QuoteRequest(R1, "계약금액"));
        var limited = service.ask(USER, new QuoteRequest(R1, "계약금액"));
        assertEquals("NONE", limited.method());
    }

    @Test
    void comparesTwoFilingsAndKeepsChangedPassagesVerbatim() {
        String amended = TEXT.replace("1,234억원", "1,300억원") + "\n정정 사유를 새로 적었습니다 확인하세요.";
        var source = new FakeSource(Map.of(R1, TEXT, R2, amended));
        var response = service(new FakeModel("계약금액이 1,234억원에서 1,300억원으로 바뀌었고 정정 사유 문단이 추가되었습니다.", false), source)
                .compare(USER, new CompareRequest(R1, R2));
        assertEquals("AI", response.method());
        assertEquals("계약금액 | 1,234억원", response.changes().get(0).before());
        assertEquals("계약금액 | 1,300억원", response.changes().get(0).after());
        assertEquals(2, response.totalChanges());
        assertFalse(response.truncated());
    }

    @Test
    void compareFallsBackToChangedPassagesWhenTheNoteGivesAReason() {
        String amended = TEXT.replace("1,234억원", "1,300억원");
        var source = new FakeSource(Map.of(R1, TEXT, R2, amended));
        var response = service(new FakeModel("계약금액이 바뀐 것은 시장 영향 때문입니다.", false), source)
                .compare(USER, new CompareRequest(R1, R2));
        assertEquals("RULE", response.method());
        assertNull(response.explanation());
        assertEquals(1, response.changes().size());
    }

    @Test
    void compareRejectsSameOrInvalidReceipts() {
        var service = service(null, source());
        assertThrows(IllegalArgumentException.class, () -> service.compare(USER, new CompareRequest(R1, R1)));
        assertThrows(IllegalArgumentException.class, () -> service.compare(USER, new CompareRequest(R1, "x")));
    }
}
