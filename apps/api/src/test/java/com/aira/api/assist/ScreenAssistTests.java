package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ScreenAssistTests {
    static ReadingAssistModel model(String answer) {
        return new ReadingAssistModel() {
            public boolean available() { return true; }
            public String providerKey() { return "TEST"; }
            public String modelKey() { return "test"; }
            public String complete(String prompt) { return answer; }
        };
    }

    private final Clock clock = Clock.fixed(Instant.parse("2026-10-12T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void keepsOnlyWhitelistedFiltersWithinRange() {
        var service = new ReadingAssistService(model("설명 {\"opMin\":0.1,\"debtMax\":100,\"perMax\":-5,\"hack\":1,\"market\":\"KOSDAQ\",\"industry\":\"26\"} 끝"), clock, JsonMapper.builder().build());
        var out = service.screen(UUID.randomUUID(), "빚이 적고 흑자인 코스닥 반도체");
        assertTrue(out.understood());
        assertEquals("0.1", out.filters().get("opMin"));
        assertEquals("100", out.filters().get("debtMax"));
        assertNull(out.filters().get("perMax"));
        assertFalse(out.filters().containsKey("hack"));
        assertEquals("KOSDAQ", out.market());
        assertEquals("26", out.industry());
    }

    @Test
    void rejectsEmptyOrBrokenAnswers() {
        var json = JsonMapper.builder().build();
        assertFalse(new ReadingAssistService(model("{\"market\":\"NYSE\",\"industry\":\"반도체\"}"), clock, json).screen(UUID.randomUUID(), "아무거나").understood());
        assertFalse(new ReadingAssistService(model("모르겠어요"), clock, json).screen(UUID.randomUUID(), "아무거나").understood());
        assertFalse(new ReadingAssistService(null, clock, json).screen(UUID.randomUUID(), "흑자").understood());
        assertThrows(IllegalArgumentException.class, () -> new ReadingAssistService(model("{}"), clock, json).screen(UUID.randomUUID(), " "));
    }

    @Test
    void newReadingKindsAreAcceptedAndGuarded() {
        var json = JsonMapper.builder().build();
        var facts = List.of(new AssistRequest.Fact("부채비율", "31.5%", "2025 말"));
        var ok = new ReadingAssistService(model("부채비율은 빚을 자본으로 나눈 값이고, 이 회사는 31.5%입니다."), clock, json)
                .assist(UUID.randomUUID(), new AssistRequest("TERM", "부채비율", facts));
        assertEquals("AI", ok.method());
        var blocked = new ReadingAssistService(model("31.5%라서 매수하세요."), clock, json)
                .assist(UUID.randomUUID(), new AssistRequest("CHECK", "", facts));
        assertEquals("RULE", blocked.method());
    }

    @Test
    void questionAnswersStayInsideTheGivenFacts() {
        var json = JsonMapper.builder().build();
        var facts = List.of(new AssistRequest.Fact("2025 매출", "333.61조 원", "2025 사업연도"), new AssistRequest.Fact("2024 매출", "300.87조 원", "2024 사업연도"));
        var ok = new ReadingAssistService(model("2025 사업연도 매출은 333.61조 원으로, 2024 사업연도 300.87조 원보다 많습니다."), clock, json)
                .assist(UUID.randomUUID(), new AssistRequest("QA", "매출이 늘었어?", facts));
        assertEquals("AI", ok.method());
        var invented = new ReadingAssistService(model("내년 매출은 400조 원이 될 것입니다."), clock, json)
                .assist(UUID.randomUUID(), new AssistRequest("QA", "내년 매출은?", facts));
        assertEquals("RULE", invented.method());
    }

    @Test
    void whyExplanationsUseOnlyTheComponents() {
        var json = JsonMapper.builder().build();
        var facts = List.of(new AssistRequest.Fact("영업이익", "32.7조 원", "2024"), new AssistRequest.Fact("매출", "300.87조 원", "2024"),
                new AssistRequest.Fact("영업이익", "43.6조 원", "2025"), new AssistRequest.Fact("매출", "333.61조 원", "2025"));
        var ok = new ReadingAssistService(model("영업이익이 32.7조 원에서 43.6조 원으로 늘어난 폭이 매출(300.87조 원→333.61조 원)보다 커서 비율이 올랐습니다."), clock, json)
                .assist(UUID.randomUUID(), new AssistRequest("WHY", "영업이익률 (2024 → 2025)", facts));
        assertEquals("AI", ok.method());
        var guess = new ReadingAssistService(model("반도체 업황이 좋아져 영업이익이 50조 원에 가까워졌습니다."), clock, json)
                .assist(UUID.randomUUID(), new AssistRequest("WHY", "영업이익률 (2024 → 2025)", facts));
        assertEquals("RULE", guess.method());
    }
}
