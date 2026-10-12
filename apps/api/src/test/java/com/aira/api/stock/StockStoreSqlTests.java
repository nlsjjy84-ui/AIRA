package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

/** 운영과 같은 SQL이 실제 DB(H2, PostgreSQL 모드)에서 도는지 확인한다. */
class StockStoreSqlTests {
    private JdbcTemplate jdbc;
    private final UUID samsung = UUID.randomUUID();
    private final UUID hynix = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        jdbc = new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:stock-sql-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        jdbc.execute("CREATE TABLE entity (id UUID PRIMARY KEY, entity_type VARCHAR(24), canonical_name VARCHAR(300), symbol VARCHAR(64))");
        jdbc.execute("CREATE TABLE entity_external_identifier (entity_id UUID, namespace VARCHAR(64), identifier_type VARCHAR(64), identifier_value VARCHAR(255))");
        new StockSchema(jdbc).ensure();
        for (Object[] row : new Object[][]{{samsung, "삼성전자", "00126380"}, {hynix, "SK하이닉스", "00164779"}}) {
            jdbc.update("INSERT INTO entity(id, entity_type, canonical_name) VALUES(?, 'COMPANY', ?)", row[0], row[1]);
            jdbc.update("INSERT INTO entity_external_identifier VALUES(?, 'OPENDART', 'CORP_CODE', ?)", row[0], row[2]);
        }
    }

    @Test
    void pricesSaveReplaceAndReadBack() {
        var store = new StockPriceStore(jdbc);
        LocalDate day = LocalDate.of(2026, 10, 8);
        store.saveDay("KOSPI", day, List.of(new StockPriceStore.Row("005930", day, "KOSPI", new BigDecimal("262000"), null, null, null,
                new BigDecimal("-2.4"), null, null, new BigDecimal("1500000000000000"), 5919637922L)));
        store.saveDay("KOSPI", day, List.of(new StockPriceStore.Row("005930", day, "KOSPI", new BigDecimal("263000"), null, null, null,
                null, 100L, null, null, null)));
        var series = store.series("005930", day.minusDays(5));
        assertEquals(1, series.size());
        assertEquals(0, new BigDecimal("263000").compareTo(series.get(0).close()));
        assertEquals(100L, series.get(0).volume());
        assertEquals(java.util.Set.of(day), store.loadedDays("KOSPI", day.minusDays(1)));
    }

    @Test
    void financialHistoryKeepsTheNewestReportAndQuartersRoundTrip() {
        var store = new FinancialHistoryStore(jdbc);
        store.saveChunk(2022, "00126380", List.of(new FinancialHistoryRows.Value("00126380", 2022, "REVENUE", new BigDecimal("300"), "CFS", "old", 2022)));
        store.saveChunk(2025, "00126380", List.of(new FinancialHistoryRows.Value("00126380", 2023, "REVENUE", new BigDecimal("259"), "CFS", "new", 2025)));
        store.saveChunk(2022, "00126380", List.of(new FinancialHistoryRows.Value("00126380", 2023, "REVENUE", new BigDecimal("1"), "CFS", "older", 2022)));
        var values = store.forCompany("00126380");
        assertEquals(2, values.size());
        assertEquals("new", values.stream().filter(v -> v.fiscalYear() == 2023).findFirst().orElseThrow().receiptNo());
        assertEquals(java.util.Set.of("00126380"), store.loadedChunks(2025));
        store.saveQuarterChunk(2026, "Q1", "00126380", List.of(new FinancialHistoryRows.Cumulative("00126380", 2026, "Q1", "REVENUE", BigDecimal.TEN, "q")));
        assertEquals(1, store.quartersFor("00126380").size());
        assertEquals(java.util.Set.of("00126380"), store.loadedQuarterChunks(2026, "Q1"));
    }

    @Test
    void profilesPeersAndScreenerJoinEverything() {
        var profiles = new ProfileStore(jdbc, JsonMapper.builder().build());
        profiles.save("00126380", Map.of("induty_code", "264", "stock_code", "005930", "corp_cls", "Y", "ceo_nm", "전영현"), Instant.now());
        profiles.save("00164779", Map.of("induty_code", "26110", "stock_code", "000660", "corp_cls", "Y"), Instant.now());
        assertEquals("전영현", profiles.find("00126380").payload().get("ceo_nm"));
        assertEquals(2, profiles.peers("26").size());
        assertEquals(java.util.Set.of("00126380", "00164779"), profiles.freshCorpCodes(Instant.now().minusSeconds(60)));
        var fin = new FinancialHistoryStore(jdbc);
        fin.saveChunk(2025, "00126380", List.of(new FinancialHistoryRows.Value("00126380", 2025, "REVENUE", new BigDecimal("333"), "CFS", "r", 2025),
                new FinancialHistoryRows.Value("00126380", 2024, "REVENUE", new BigDecimal("300"), "CFS", "r", 2025)));
        var px = new StockPriceStore(jdbc);
        LocalDate day = LocalDate.now().minusDays(2);
        px.saveDay("KOSPI", day, List.of(new StockPriceStore.Row("005930", day, "KOSPI", new BigDecimal("262000"), null, null, null, new BigDecimal("1.5"),
                10L, new BigDecimal("999"), new BigDecimal("1500"), null)));
        assertEquals(new BigDecimal("333"), profiles.latestFinancials(List.of("00126380", "00164779")).get("00126380").get("REVENUE"));
        assertEquals(0, new BigDecimal("1500").compareTo((BigDecimal) profiles.latestPrices(List.of("005930", "000660")).get("005930").get("marketCap")));
        @SuppressWarnings("unchecked") var rows = (List<Map<String, Object>>) new ScreenerService(jdbc).table().get("rows");
        assertEquals(2, rows.size());
        var samsungRow = rows.stream().filter(r -> "삼성전자".equals(r.get("name"))).findFirst().orElseThrow();
        assertEquals(2025, samsungRow.get("year"));
        assertEquals(new BigDecimal("300"), samsungRow.get("revenuePrev"));
        assertTrue(((BigDecimal) samsungRow.get("high52")).compareTo(new BigDecimal("262000")) == 0);
        assertEquals(1, new StockCompanyLookup(jdbc).companyIdsByCorpCode(List.of("00126380")).size());
        assertEquals(2, new StockCompanyLookup(jdbc).allCorpCodes().size());
    }

    @Test
    void storageTrimsOldPricesAndEmptiesTheTableWhenMostRowsAreOld() {
        var store = new StockPriceStore(jdbc);
        LocalDate today = LocalDate.of(2026, 10, 12);
        for (int i = 0; i < 10; i++) {
            LocalDate day = today.minusDays(i * 30L);
            store.saveDay("KOSPI", day, List.of(new StockPriceStore.Row("005930", day, "KOSPI", new BigDecimal("1000"), null, null, null, null, null, null, null, null)));
        }
        var storage = new StockStorage(jdbc, 400);
        assertEquals(null, storage.databaseBytes(), "size is only known on PostgreSQL");
        assertTrue(!storage.nearlyFull());
        // 300일 전 이후만 남기면 오래된 줄은 0개 → 아무것도 안 지움
        assertEquals(0, storage.trimPrices(today.minusDays(300)));
        // 240일 전보다 오래된 줄 1개(270일) → 10% 이므로 그 줄만 지운다
        assertEquals(1, storage.trimPrices(today.minusDays(240)));
        assertEquals(9, store.series("005930", today.minusDays(400)).size());
        assertEquals(9, store.loadedDays("KOSPI", today.minusDays(400)).size());
        // 100일 전보다 오래된 줄이 30% 이상 → 표를 비우고 받은 날짜 기록도 비운다
        assertEquals(-1, storage.trimPrices(today.minusDays(100)));
        assertTrue(store.series("005930", today.minusDays(400)).isEmpty());
        assertTrue(store.loadedDays("KOSPI", today.minusDays(400)).isEmpty());
        assertEquals(400L, storage.report().get("softLimitMb"));
    }
}
