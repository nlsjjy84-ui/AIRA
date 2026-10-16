package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** KRX ETF 일별매매정보: 비어 있는 날(주말·공개 전)은 건너뛰고, 거래대금 순으로 정렬하고, 괴리율을 계산한다. */
class EtfServiceTests {
    @Test
    @SuppressWarnings("unchecked")
    void walksBackToTheLatestTradingDay() {
        String friday = "{\"OutBlock_1\":[{\"BAS_DD\":\"20261016\",\"ISU_CD\":\"069500\",\"ISU_NM\":\"KODEX 200\",\"TDD_CLSPRC\":\"55,000\",\"NAV\":\"54,945\",\"FLUC_RT\":\"-1.2\",\"ACC_TRDVAL\":\"900000000000\",\"IDX_IND_NM\":\"코스피 200\"},"
                + "{\"BAS_DD\":\"20261016\",\"ISU_CD\":\"360750\",\"ISU_NM\":\"TIGER 미국S&P500\",\"TDD_CLSPRC\":\"20000\",\"NAV\":\"20010\",\"ACC_TRDVAL\":\"1000000000000\",\"NEW_FIELD\":\"x\"}]}";
        var service = new EtfService((uri, key) -> uri.toString().endsWith("20261016") ? friday : "{\"OutBlock_1\":[]}",
                JsonMapper.builder().build(), "key", Clock.fixed(Instant.parse("2026-10-18T01:00:00Z"), ZoneOffset.UTC));
        Map<String, Object> board = service.board();
        assertEquals("AVAILABLE", board.get("state"));
        assertEquals("2026-10-16", board.get("date"));
        var items = (List<Map<String, Object>>) board.get("items");
        assertEquals("TIGER 미국S&P500", items.get(0).get("name"));
        assertEquals(new BigDecimal("0.10"), items.get(1).get("premium"));
        assertEquals(new BigDecimal("-0.05"), items.get(0).get("premium"));
    }
}
