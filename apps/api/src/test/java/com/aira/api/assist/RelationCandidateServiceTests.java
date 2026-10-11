package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.assist.FilingDtos.CandidateEvent;
import com.aira.api.assist.FilingDtos.CandidateRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RelationCandidateServiceTests {
    private static final tools.jackson.databind.ObjectMapper MAPPER = new tools.jackson.databind.ObjectMapper();
    private static final UUID USER = UUID.randomUUID();
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC);

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

    private static CandidateRequest request() {
        return new CandidateRequest("가나다", List.of(
                new CandidateEvent("e1", "유상증자 결정", "CAPITAL_RAISE", "2026-03-04"),
                new CandidateEvent("e2", "유상증자 신주 발행 결과", "CAPITAL_RAISE", "2026-03-20"),
                new CandidateEvent("e3", "대표이사 변경", "GOVERNANCE", "2026-09-01")));
    }

    private static RelationCandidateService service(ReadingAssistModel model) {
        return new RelationCandidateService(model, CLOCK, MAPPER);
    }

    @Test
    void keepsAnAiPairWhoseIdsAndReasonPass() {
        String answer = "{\"candidates\":[{\"a\":\"e1\",\"b\":\"e2\",\"reason\":\"두 사건 제목에 모두 유상증자가 나옵니다.\"}]}";
        var response = service(new FakeModel(answer, false)).suggest(USER, request());
        assertEquals("AI", response.method());
        assertEquals(1, response.candidates().size());
        assertEquals("e1", response.candidates().get(0).firstId());
    }

    @Test
    void dropsUnknownIdsDuplicatesAndCausalReasons() {
        String answer = "{\"candidates\":[{\"a\":\"e1\",\"b\":\"zz\",\"reason\":\"제목이 비슷합니다.\"},"
                + "{\"a\":\"e1\",\"b\":\"e2\",\"reason\":\"증자 때문에 발행이 이어졌습니다.\"},"
                + "{\"a\":\"e1\",\"b\":\"e2\",\"reason\":\"10조원 규모라 중요합니다.\"}]}";
        var response = service(new FakeModel(answer, false)).suggest(USER, request());
        assertEquals("RULE", response.method());
    }

    @Test
    void fallsBackToTypeAndDatePairsWithoutAi() {
        var response = service(null).suggest(USER, request());
        assertEquals("RULE", response.method());
        assertEquals(1, response.candidates().size());
        assertEquals("e1", response.candidates().get(0).firstId());
        assertEquals("e2", response.candidates().get(0).secondId());
        assertTrue(response.candidates().get(0).reason().contains("유형이 같습니다"));
        assertTrue(response.candidates().get(0).reason().contains("16일"));
        var failing = service(new FakeModel("", true)).suggest(USER, request());
        assertEquals("RULE", failing.method());
    }

    @Test
    void needsAtLeastTwoEventsAndAValidRequest() {
        var one = service(null).suggest(USER, new CandidateRequest("x", List.of(new CandidateEvent("e1", "a", "T", "2026-01-01"))));
        assertEquals("NONE", one.method());
        assertThrows(IllegalArgumentException.class, () -> service(null).suggest(USER, new CandidateRequest("x", List.of())));
        assertThrows(IllegalArgumentException.class, () -> service(null).suggest(USER, null));
    }
}
