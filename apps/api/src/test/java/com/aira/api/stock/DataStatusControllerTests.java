package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 자료 상태: 기준일과 수집 시각을 따로 보고, 매일 자료가 멈추면 '늦음'으로 알린다. */
class DataStatusControllerTests {
    private static final Instant NOW = Instant.parse("2026-10-16T03:00:00Z"); // 한국 시각 10/16 12:00

    @Test
    void freshDailyPriceIsOk() {
        var s = DataStatusController.judgeDaily("STOCK_PRICE", "종목 주가", "KRX", true,
                "2026-10-15", Instant.parse("2026-10-15T09:30:00Z"), NOW);
        assertEquals("OK", s.state());
        assertTrue(s.note().contains("실시간 시세가 아닙니다"));
    }

    @Test
    void collectionStoppedForMoreThan36HoursIsLate() {
        var s = DataStatusController.judgeDaily("STOCK_PRICE", "종목 주가", "KRX", true,
                "2026-10-14", Instant.parse("2026-10-14T09:30:00Z"), NOW);
        assertEquals("LATE", s.state());
        assertTrue(s.note().contains("자동 수집이 멈췄을 수 있어요"));
    }

    @Test
    void reCollectingOldTradingDayDoesNotMakeItCurrent() {
        // 오늘 다시 가져왔더라도 기준 거래일이 6일 전이면 늦음이다(수집 시각과 기준일을 섞지 않는다).
        var s = DataStatusController.judgeDaily("MARKET_INDEX", "시장 지수", "KRX", true,
                "2026-10-10", Instant.parse("2026-10-16T02:00:00Z"), NOW);
        assertEquals("LATE", s.state());
        assertTrue(s.note().contains("6일 전"));
    }

    @Test
    void longWeekendWithinFiveDaysIsStillOk() {
        var s = DataStatusController.judgeDaily("STOCK_PRICE", "종목 주가", "KRX", true,
                "2026-10-12", Instant.parse("2026-10-16T01:00:00Z"), NOW);
        assertEquals("OK", s.state());
    }

    @Test
    void nothingStoredYetIsUnknownNotLate() {
        var s = DataStatusController.judgeDaily("MARKET_NEWS", "시장 뉴스", "뉴스 검색", false, null, null, NOW);
        assertEquals("UNKNOWN", s.state());
    }
}
