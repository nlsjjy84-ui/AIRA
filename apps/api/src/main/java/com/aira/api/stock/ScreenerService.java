package com.aira.api.stock;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 조건으로 종목 찾기: 전 상장사의 최근 사업연도 재무 값·직전 연도 매출·최근 거래일 시세를 한 표로 모은다.
 * 거르기·정렬은 화면에서 한다. 결과는 조건에 맞는 회사 목록일 뿐 추천이 아니다.
 */
@Service
public class ScreenerService {
    private static final Duration KEEP = Duration.ofMinutes(30);
    private final JdbcTemplate jdbc;
    private final Clock clock;
    private volatile Map.Entry<Instant, Map<String, Object>> cache;

    @Autowired
    public ScreenerService(JdbcTemplate jdbc) { this(jdbc, Clock.systemUTC()); }

    ScreenerService(JdbcTemplate jdbc, Clock clock) { this.jdbc = jdbc; this.clock = clock; }

    public Map<String, Object> table() {
        var current = cache;
        if (current != null && current.getKey().plus(KEEP).isAfter(clock.instant())) return current.getValue();
        Map<String, Object> out = build();
        cache = Map.entry(clock.instant(), out);
        return out;
    }

    record Company(UUID id, String name, String corpCode, String stockCode, String corpCls, String induty) {}

    Map<String, Object> build() {
        List<Company> companies = jdbc.query("""
                SELECT x.entity_id, e.canonical_name, p.corp_code, p.stock_code, p.corp_cls, p.induty_code
                FROM company_profile_cache p
                JOIN entity_external_identifier x ON x.identifier_value = p.corp_code AND x.namespace='OPENDART' AND x.identifier_type='CORP_CODE'
                JOIN entity e ON e.id = x.entity_id AND e.entity_type='COMPANY'
                WHERE p.corp_cls IN ('Y','K') AND p.stock_code IS NOT NULL
                """, (rs, i) -> new Company(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6)));
        Map<String, Map<Integer, Map<String, BigDecimal>>> fin = new HashMap<>();
        jdbc.query("SELECT corp_code, fiscal_year, account, amount FROM company_financial_history WHERE fiscal_year >= ?",
                rs -> { fin.computeIfAbsent(rs.getString(1), k -> new HashMap<>()).computeIfAbsent(rs.getInt(2), k -> new HashMap<>())
                        .put(rs.getString(3), rs.getBigDecimal(4)); },
                java.time.LocalDate.now(clock).getYear() - 4);
        Map<String, Object[]> px = new HashMap<>();
        List<java.time.LocalDate> latest = jdbc.query("SELECT max(trading_date) FROM stock_price_daily WHERE market IN ('KOSPI','KOSDAQ')",
                (rs, i) -> rs.getObject(1, java.time.LocalDate.class));
        java.time.LocalDate day = latest.isEmpty() ? null : latest.get(0);
        Map<String, Object[]> range = new HashMap<>();
        if (day != null) {
            jdbc.query("SELECT short_code, close_price, market_cap, change_rate, trading_value, trading_volume FROM stock_price_daily WHERE trading_date = ? AND market IN ('KOSPI','KOSDAQ')",
                    rs -> { px.put(rs.getString(1), new Object[]{rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getObject(6)}); },
                    java.sql.Date.valueOf(day));
            jdbc.query("""
                    SELECT short_code, max(close_price), min(close_price), count(*) FROM stock_price_daily
                    WHERE trading_date > ? AND market IN ('KOSPI','KOSDAQ') GROUP BY short_code
                    """, rs -> { range.put(rs.getString(1), new Object[]{rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getInt(4)}); },
                    java.sql.Date.valueOf(day.minusDays(365)));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Company c : companies) {
            var years = fin.getOrDefault(c.corpCode(), Map.of());
            Integer year = years.keySet().stream().max(Integer::compareTo).orElse(null);
            Map<String, BigDecimal> now = year == null ? Map.of() : years.get(year);
            Map<String, BigDecimal> prev = year == null ? Map.of() : years.getOrDefault(year - 1, Map.of());
            Object[] p = px.get(c.stockCode());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("companyId", c.id());
            row.put("name", c.name());
            row.put("market", "Y".equals(c.corpCls()) ? "KOSPI" : "KOSDAQ");
            row.put("indutyCode", c.induty());
            row.put("year", year);
            row.put("revenue", now.get("REVENUE"));
            row.put("revenuePrev", prev.get("REVENUE"));
            row.put("operatingIncome", now.get("OPERATING_INCOME"));
            row.put("netIncome", now.get("NET_INCOME"));
            row.put("equity", now.get("TOTAL_EQUITY"));
            row.put("liabilities", now.get("TOTAL_LIABILITIES"));
            row.put("assets", now.get("TOTAL_ASSETS"));
            row.put("close", p == null ? null : p[0]);
            row.put("marketCap", p == null ? null : p[1]);
            row.put("changeRate", p == null ? null : p[2]);
            row.put("tradingValue", p == null ? null : p[3]);
            row.put("volume", p == null ? null : p[4]);
            Object[] r = range.get(c.stockCode());
            row.put("high52", r == null ? null : r[0]);
            row.put("low52", r == null ? null : r[1]);
            row.put("days52", r == null ? null : r[2]);
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "OpenDART 주요계정(사업보고서) · 기업개황 · KRX 일별 매매정보");
        out.put("priceDate", day == null ? null : day.toString());
        out.put("state", rows.isEmpty() ? "NO_DATA" : "AVAILABLE");
        out.put("rows", rows);
        return out;
    }
}
