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

/** ECOS 통계검색 응답에서 최근 값과 직전 값, 변화량을 만든다. 빈 값은 건너뛴다. */
class MarketIndicatorServiceTests {
    @Test
    @SuppressWarnings("unchecked")
    void buildsLatestAndChange() {
        String body = "{\"StatisticSearch\":{\"list_total_count\":3,\"row\":["
                + "{\"TIME\":\"20261014\",\"DATA_VALUE\":\"1348.5\"},{\"TIME\":\"20261015\",\"DATA_VALUE\":\"1,337.3\"},{\"TIME\":\"20261016\",\"DATA_VALUE\":\"\"}]}}";
        var service = new MarketIndicatorService(uri -> body, JsonMapper.builder().build(), "key",
                Clock.fixed(Instant.parse("2026-10-16T06:00:00Z"), ZoneOffset.UTC));
        Map<String, Object> board = service.board();
        assertEquals("AVAILABLE", board.get("state"));
        var usd = ((List<Map<String, Object>>) board.get("items")).get(0);
        assertEquals("USD", usd.get("key"));
        assertEquals("2026-10-15", usd.get("time"));
        assertEquals(new BigDecimal("1337.3"), usd.get("value"));
        assertEquals(new BigDecimal("-11.2"), usd.get("change"));
    }

    @Test
    void noKeyMeansUnavailable() {
        var service = new MarketIndicatorService(uri -> "", JsonMapper.builder().build(), "", Clock.systemUTC());
        assertEquals("UNAVAILABLE", service.board().get("state"));
    }
}
