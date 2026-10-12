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

    @Test
    void causalRankingSkipsAccountingPolicyAndUnrelatedChangeWords() {
        var passages = List.of(
                "연결회사는 고객이 반품할 것으로 예상되는 제품에 대하여 환불부채를 인식하고 있으며, 수익은 예측된 반품률의 변동에 영향을 받습니다.",
                "당사는 환율변동위험에 노출되어 있으며 환율변동 영향을 최소화하는데 주력하고 있습니다.",
                "2025년 전 세계 자동차 생산량은 전년 대비 3.7% 증가하였습니다. 이는 중국의 견조한 실적에 기인하였습니다.",
                "2025년 영업이익은 메모리 판매 가격 상승과 HBM 판매 확대 영향으로 전년 대비 33.2% 증가하였습니다.");
        var top = FilingText.rank(passages, "영업이익 증가 원인 매출 변동 이유", 3);
        assertEquals(3, top.get(0));
        assertEquals(false, top.contains(0), "accounting policy is not a reason");
        assertEquals(false, top.contains(1), "currency risk shares only the word 변동");
    }
}
