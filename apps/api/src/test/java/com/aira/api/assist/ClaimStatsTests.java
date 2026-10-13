package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aira.api.assist.ClaimDtos.ClaimResult;
import com.aira.api.stock.StockSchema;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class ClaimStatsTests {
    private static ClaimResult result(String verdict, String item) {
        return new ClaimResult("말", verdict, "삼성전자", item, 2025, null, null, null, null, null, null);
    }

    @Test
    void countsOnlyVerdictsAndItemsWithinTheWindow() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:claimstats-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        new StockSchema(jdbc).ensure();
        var day1 = new ClaimStats(jdbc, Clock.fixed(Instant.parse("2026-09-01T03:00:00Z"), ZoneOffset.UTC));
        var today = new ClaimStats(jdbc, Clock.fixed(Instant.parse("2026-10-13T03:00:00Z"), ZoneOffset.UTC));
        day1.record(List.of(result("MISMATCH", "영업이익")));
        today.record(List.of(result("MATCH", "영업이익"), result("MISMATCH", "영업이익"), result("MISMATCH", "매출")));
        today.record(List.of(result("NOT_CHECKABLE", null), result("WEIRD", "매출")));
        today.record(List.of());

        Map<String, Object> month = today.summary(30);
        assertEquals("2026-09-14", month.get("since"));
        assertEquals(5, month.get("total"));
        assertEquals(3, month.get("checkable"));
        assertEquals(2, month.get("mismatches"));
        org.junit.jupiter.api.Assertions.assertTrue(((List<?>) month.get("byItem")).contains(Map.of("item", "영업이익", "checks", 2, "mismatches", 1)));
        org.junit.jupiter.api.Assertions.assertTrue(((List<?>) month.get("byItem")).contains(Map.of("item", "매출", "checks", 2, "mismatches", 1)));
        assertEquals(6, today.summary(60).get("total"), "the older check is inside a 60-day window");
        assertEquals(0, new ClaimStats(new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:empty-" + UUID.randomUUID(), "sa", ""))).summary(30).get("total"),
                "a missing table never breaks the page");
    }
}
