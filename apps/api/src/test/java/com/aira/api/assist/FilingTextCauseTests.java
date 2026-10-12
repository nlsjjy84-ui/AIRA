package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class FilingTextCauseTests {
    @Test
    void causalQuestionsPreferPassagesThatGiveReasons() {
        var passages = List.of(
                "2025년 전 세계 자동차 생산량은 전년 대비 3.7% 증가하였습니다.",
                "2025년 당사의 매출은 333조 6,059억원으로 전년 동기 대비 10.9% 증가하였습니다.",
                "DS 부문 영업이익은 메모리 가격 상승과 고대역폭 메모리 판매 확대 영향으로 크게 증가하였습니다.",
                "연결대상 종속기업은 308개사입니다.");
        var top = FilingText.rank(passages, "2024년 대비 2025년 영업이익 증가 원인", 3);
        assertEquals(2, top.get(0));
    }
}
