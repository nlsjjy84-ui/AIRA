package com.aira.api.stock;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 종목 화면 그래프용 KRX 일별 매매정보 요약 저장소. */
@Repository
public class StockPriceStore {
    private final JdbcTemplate jdbc;

    public StockPriceStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Row(String shortCode, LocalDate date, String market, BigDecimal close, BigDecimal open, BigDecimal high,
            BigDecimal low, BigDecimal changeRate, Long volume, BigDecimal value, BigDecimal marketCap, Long listedShares) {}

    public Set<LocalDate> loadedDays(String market, LocalDate from) {
        return Set.copyOf(jdbc.query("SELECT trading_date FROM stock_price_day_loaded WHERE market=? AND trading_date>=?",
                (rs, i) -> rs.getObject(1, LocalDate.class), market, Date.valueOf(from)));
    }

    @Transactional
    public void saveDay(String market, LocalDate date, List<Row> rows) {
        jdbc.update("DELETE FROM stock_price_daily WHERE market=? AND trading_date=?", market, Date.valueOf(date));
        jdbc.batchUpdate("""
                INSERT INTO stock_price_daily(short_code,trading_date,market,close_price,open_price,high_price,low_price,
                    change_rate,trading_volume,trading_value,market_cap,listed_shares)
                VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
                """, rows, 500, (ps, r) -> {
            ps.setString(1, r.shortCode()); ps.setDate(2, Date.valueOf(r.date())); ps.setString(3, r.market());
            ps.setBigDecimal(4, r.close()); ps.setBigDecimal(5, r.open()); ps.setBigDecimal(6, r.high());
            ps.setBigDecimal(7, r.low()); ps.setBigDecimal(8, r.changeRate());
            if (r.volume() == null) ps.setNull(9, java.sql.Types.BIGINT); else ps.setLong(9, r.volume());
            ps.setBigDecimal(10, r.value()); ps.setBigDecimal(11, r.marketCap());
            if (r.listedShares() == null) ps.setNull(12, java.sql.Types.BIGINT); else ps.setLong(12, r.listedShares());
        });
        jdbc.update("DELETE FROM stock_price_day_loaded WHERE market=? AND trading_date=?", market, Date.valueOf(date));
        jdbc.update("INSERT INTO stock_price_day_loaded(market,trading_date,row_count,loaded_at) VALUES(?,?,?,?)",
                market, Date.valueOf(date), rows.size(), OffsetDateTime.now());
    }

    public List<Row> series(String shortCode, LocalDate from) {
        return jdbc.query("""
                SELECT short_code,trading_date,market,close_price,open_price,high_price,low_price,change_rate,
                       trading_volume,trading_value,market_cap,listed_shares
                FROM stock_price_daily WHERE short_code=? AND trading_date>=? ORDER BY trading_date
                """, (rs, i) -> new Row(rs.getString(1), rs.getObject(2, LocalDate.class), rs.getString(3),
                        rs.getBigDecimal(4), rs.getBigDecimal(5), rs.getBigDecimal(6), rs.getBigDecimal(7), rs.getBigDecimal(8),
                        (Long) rs.getObject(9, Long.class), rs.getBigDecimal(10), rs.getBigDecimal(11),
                        (Long) rs.getObject(12, Long.class)), shortCode, Date.valueOf(from));
    }

    /** 그 종목이 속한 시장의 가장 최근 거래일 기준 시가총액·거래대금 순위. 자료가 없으면 null. */
    public java.util.Map<String, Object> ranks(String shortCode) {
        var rows = jdbc.queryForList("""
                WITH d AS (SELECT market, MAX(trading_date) td FROM stock_price_daily WHERE short_code=? GROUP BY market)
                SELECT x.trading_date, x.market, x.cap_rank, x.value_rank, x.total FROM (
                    SELECT s.short_code, s.trading_date, s.market,
                           RANK() OVER (ORDER BY s.market_cap DESC NULLS LAST) cap_rank,
                           RANK() OVER (ORDER BY s.trading_value DESC NULLS LAST) value_rank,
                           COUNT(*) OVER () total
                    FROM stock_price_daily s JOIN d ON s.market=d.market AND s.trading_date=d.td
                    WHERE LENGTH(s.short_code)=6 AND s.market_cap IS NOT NULL) x
                WHERE x.short_code=?
                """, shortCode, shortCode);
        if (rows.isEmpty()) return null;
        var r = rows.get(0);
        java.util.Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("date", String.valueOf(r.get("trading_date")));
        out.put("market", r.get("market"));
        out.put("marketCapRank", ((Number) r.get("cap_rank")).intValue());
        out.put("tradingValueRank", ((Number) r.get("value_rank")).intValue());
        out.put("total", ((Number) r.get("total")).intValue());
        return out;
    }
}
