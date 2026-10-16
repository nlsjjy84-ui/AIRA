package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** KRX 지수 시리즈·금시장: 비어 있는 날은 건너뛰고, 값이 '-'인 지수는 빼고, 시리즈 이름에 맞는 주소를 부른다. */
class KrxBoardServiceTests {
    private static final Clock SAT = Clock.fixed(Instant.parse("2026-10-17T01:00:00Z"), ZoneOffset.UTC);

    @Test
    @SuppressWarnings("unchecked")
    void indicesWalkBackAndSkipBlankRows() {
        List<String> called = new ArrayList<>();
        String friday = "{\"OutBlock_1\":[{\"BAS_DD\":\"20261016\",\"IDX_CLSS\":\"KRX\",\"IDX_NM\":\"KRX 반도체\",\"CLSPRC_IDX\":\"5,123.45\",\"CMPPREVDD_IDX\":\"-12.30\",\"FLUC_RT\":\"-0.24\",\"MKTCAP\":\"1000\"},"
                + "{\"IDX_CLSS\":\"KRX\",\"IDX_NM\":\"KRX 신규\",\"CLSPRC_IDX\":\"-\"}]}";
        var service = new KrxBoardService((uri, key) -> { called.add(uri.toString()); return uri.toString().endsWith("20261016") ? friday : "{\"OutBlock_1\":[]}"; },
                JsonMapper.builder().build(), "key", SAT);
        Map<String, Object> board = service.indices("nope");
        assertEquals("krx", board.get("series"));
        assertEquals("AVAILABLE", board.get("state"));
        assertEquals("2026-10-16", board.get("date"));
        var items = (List<Map<String, Object>>) board.get("items");
        assertEquals(1, items.size());
        assertEquals(new BigDecimal("5123.45"), items.get(0).get("close"));
        assertEquals(new BigDecimal("-0.24"), items.get(0).get("changeRate"));
        assertEquals("https://data-dbg.krx.co.kr/svc/apis/idx/krx_dd_trd?basDd=20261017", called.get(0));
        service.indices("krx");
        assertEquals(2, called.size(), "3시간 안에는 다시 부르지 않는다");
        service.indices("kosdaq");
        assertEquals("https://data-dbg.krx.co.kr/svc/apis/idx/kosdaq_dd_trd?basDd=20261017", called.get(2));
    }

    @Test
    @SuppressWarnings("unchecked")
    void goldReadsPricePerGram() {
        String body = "{\"OutBlock_1\":[{\"BAS_DD\":\"20261016\",\"ISU_CD\":\"04020000\",\"ISU_NM\":\"금 99.99_1Kg\",\"TDD_CLSPRC\":\"190,500\",\"CMPPREVDD_PRC\":\"1,200\",\"FLUC_RT\":\"0.63\",\"ACC_TRDVOL\":\"35,000\"}]}";
        var service = new KrxBoardService((uri, key) -> uri.toString().contains("gen/gold_bydd_trd") ? body : "{}", JsonMapper.builder().build(), "key", SAT);
        Map<String, Object> board = service.gold();
        var items = (List<Map<String, Object>>) board.get("items");
        assertEquals("금 99.99_1Kg", items.get(0).get("name"));
        assertEquals(new BigDecimal("190500"), items.get(0).get("close"));
        assertNull(items.get(0).get("high"));
    }

    @Test
    void withoutKeyItSaysUnavailable() {
        var service = new KrxBoardService((uri, key) -> { throw new AssertionError("no call"); }, JsonMapper.builder().build(), " ", SAT);
        assertEquals("UNAVAILABLE", service.gold().get("state"));
    }
}
