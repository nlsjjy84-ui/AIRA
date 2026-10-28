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

    @Test
    void spokenWhyQuestionFindsTheReasonNotTheNewAccountingStandard() {
        var passages = List.of(
                "연결회사는 연결손익계산서에서 모든 수익과 비용을 영업활동, 투자활동, 재무활동, 중단영업 및 법인세의 다섯 개 범주로 분류해야 하며, 영업이익 중간합계를 표시해야 합니다.",
                "연결회사는 새로운 회계기준이 연결회사의 연결손익계산서 구조 및 영업이익에 미치는 영향을 평가하고 있습니다.",
                "2025년 영업이익은 메모리 판매 가격 상승과 HBM 판매 확대 영향으로 전년 대비 33.2% 증가하였습니다.");
        var top = FilingText.rank(passages, "영업이익이 왜 늘었어?", 3);
        assertEquals(2, top.get(0));
        assertEquals(false, top.contains(0), "accounting standard text is not a reason");
        assertEquals(false, top.contains(1), "accounting standard impact review is not a reason");
    }

    @Test
    void readsToTheEndOfLongReportsAndSkipsTableHeadersForWhyQuestions() {
        var passages = new java.util.ArrayList<String>();
        passages.add("2025년(제57기) 영업이익은 DX 부문이 12조 8,527억원, DS 부문이 24조 8,581억원입니다.");
        passages.add("영업이익(손실)");
        for (int i = 0; i < 7000; i++) passages.add("연결대상 종속기업 현황 표의 " + i + "번째 줄입니다.");
        passages.add("제57기 당사 매출액은 전년 대비 32조 7,350억원(10.9%) 증가하였으며, 영업이익은 전년 대비 10조 8,751억원(33.2%) 증가한 43조 6,011억원을 기록하였습니다.");
        var top = FilingText.rank(passages, "영업이익이 왜 늘었어?", 3);
        assertEquals(passages.size() - 1, top.get(0));
        assertEquals(false, top.contains(1), "a table header is not a reason");
        var plain = String.join("\n", passages);
        assertEquals(passages.size(), FilingText.passages(plain).size(), "a long report is read to the end");
    }

    @Test
    void whyQuestionsPreferTheBusinessAnalysisOverExecutiveBonusReasons() {
        var passages = List.of(
                "글로벌 브랜드를 공고히 하는 한편 온라인 판매를 확대하였고, 영업이익 증가 등 실적에 기여 중인 점을 고려하여 상여금 산정",
                "제57기 당사 매출액은 전년 대비 32조 7,350억원(10.9%) 증가하였으며, 영업이익은 전년 대비 10조 8,751억원(33.2%) 증가한 43조 6,011억원을 기록하였습니다.",
                "(DS 부문) DS 부문의 제57기 매출은 130조 1,282억원으로 전년 대비 19조 0,622억원(17.2%) 증가하였으며, 영업이익은 24조 8,581억원으로 전년 대비 9조 7,636억원 증가하였습니다.");
        var top = FilingText.rank(passages, "영업이익이 왜 늘었어?", 3);
        assertEquals(false, top.contains(0), "a bonus reason is not why the company earned more");
        assertEquals(true, top.indexOf(1) >= 0 && top.indexOf(2) >= 0);
    }

    @Test
    void theSegmentThatGrewTheMostComesFirst() {
        var passages = List.of(
                "DX 부문의 제57기 매출은 187조 9,673억원으로 전년 대비 13조 0,797억원(7.5%) 증가하였으며, 영업이익은 12조 8,527억원으로 전년 대비 4,128억원(3.3%) 증가하였습니다.",
                "SDC의 제57기 매출은 29조 8,417억원으로 전년 대비 6,838억원(5.9%) 증가하였으며, 영업이익은 4조 1,163억원으로 전년 대비 3,829억원(10.3%) 증가하였습니다.",
                "(DS 부문) DS 부문의 제57기 매출은 130조 1,282억원으로 전년 대비 19조 0,622억원(17.2%) 증가하였으며, 영업이익은 24조 8,581억원으로 전년 대비 9조 7,636억원 증가하였습니다.");
        assertEquals(97_636L, FilingText.changeAmount("영업이익은 전년 대비 9조 7,636억원 증가"));
        assertEquals(4_128L, FilingText.changeAmount("영업이익은 전년 대비 4,128억원(3.3%) 증가"));
        var top = FilingText.rank(passages, "영업이익이 왜 늘었어?", 3);
        assertEquals(2, top.get(0), "DS grew the most");
    }
}
