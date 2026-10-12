package com.aira.api.stock;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** OpenDART 기업개황 저장소. 업종코드로 같은 업종 회사를 찾는 데도 쓴다. */
@Repository
public class ProfileStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public ProfileStore(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public record Cached(Map<String, String> payload, Instant fetchedAt) {}
    public record Peer(String corpCode, String stockCode, String corpCls, String indutyCode, java.util.UUID companyId, String name) {}

    public Cached find(String corpCode) {
        var rows = jdbc.query("SELECT payload, fetched_at FROM company_profile_cache WHERE corp_code=?",
                (rs, i) -> new Cached(json.readValue(rs.getString(1), new TypeReference<Map<String, String>>() {}), rs.getTimestamp(2).toInstant()), corpCode);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Transactional
    public void save(String corpCode, Map<String, String> payload, Instant at) {
        jdbc.update("DELETE FROM company_profile_cache WHERE corp_code=?", corpCode);
        jdbc.update("INSERT INTO company_profile_cache(corp_code,payload,fetched_at,induty_code,stock_code,corp_cls) VALUES(?,?,?,?,?,?)",
                corpCode, json.writeValueAsString(payload), Timestamp.from(at), trim(payload.get("induty_code")),
                trim(payload.get("stock_code")), trim(payload.get("corp_cls")));
    }

    public Set<String> freshCorpCodes(Instant since) {
        return Set.copyOf(jdbc.query("SELECT corp_code FROM company_profile_cache WHERE fetched_at>=? AND induty_code IS NOT NULL",
                (rs, i) -> rs.getString(1), Timestamp.from(since)));
    }

    /** 업종코드가 prefix로 시작하는 상장사(유가·코스닥). */
    public List<Peer> peers(String prefix) {
        return jdbc.query("""
                SELECT p.corp_code, p.stock_code, p.corp_cls, p.induty_code, x.entity_id, e.canonical_name
                FROM company_profile_cache p
                JOIN entity_external_identifier x ON x.identifier_value = p.corp_code AND x.namespace='OPENDART' AND x.identifier_type='CORP_CODE'
                JOIN entity e ON e.id = x.entity_id AND e.entity_type='COMPANY'
                WHERE p.induty_code LIKE ? AND p.corp_cls IN ('Y','K')
                """, (rs, i) -> new Peer(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getObject(5, java.util.UUID.class), rs.getString(6)), prefix + "%");
    }

    /** 회사별 가장 최근 사업연도의 재무 값. */
    public Map<String, Map<String, Object>> latestFinancials(List<String> corpCodes) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        if (corpCodes.isEmpty()) return out;
        String marks = String.join(",", Collections.nCopies(corpCodes.size(), "?"));
        jdbc.query("""
                SELECT h.corp_code, h.fiscal_year, h.account, h.amount FROM company_financial_history h
                JOIN (SELECT corp_code, max(fiscal_year) AS y FROM company_financial_history WHERE corp_code IN (%s) GROUP BY corp_code) m
                  ON m.corp_code = h.corp_code AND m.y = h.fiscal_year
                """.formatted(marks), rs -> {
            var map = out.computeIfAbsent(rs.getString(1), k -> new HashMap<>());
            map.put("year", rs.getInt(2));
            map.put(rs.getString(3), rs.getBigDecimal(4));
        }, corpCodes.toArray());
        return out;
    }

    /** 종목코드별 가장 최근 거래일의 종가·시가총액·상장주식수. */
    public Map<String, Map<String, Object>> latestPrices(List<String> shortCodes) {
        Map<String, Map<String, Object>> out = new HashMap<>();
        List<String> codes = new ArrayList<>(shortCodes.stream().filter(c -> c != null && !c.isBlank()).toList());
        if (codes.isEmpty()) return out;
        String marks = String.join(",", Collections.nCopies(codes.size(), "?"));
        jdbc.query("""
                SELECT d.short_code, d.trading_date, d.close_price, d.market_cap, d.listed_shares FROM stock_price_daily d
                JOIN (SELECT short_code, max(trading_date) AS t FROM stock_price_daily WHERE short_code IN (%s) GROUP BY short_code) m
                  ON m.short_code = d.short_code AND m.t = d.trading_date
                """.formatted(marks), rs -> {
            Map<String, Object> map = new HashMap<>();
            map.put("date", rs.getObject(2, java.time.LocalDate.class).toString());
            map.put("close", rs.getBigDecimal(3));
            map.put("marketCap", rs.getBigDecimal(4));
            map.put("listedShares", rs.getObject(5));
            out.put(rs.getString(1), map);
        }, codes.toArray());
        return out;
    }

    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    static BigDecimal ignored() { return null; }
}
