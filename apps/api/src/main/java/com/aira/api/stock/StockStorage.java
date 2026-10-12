package com.aira.api.stock;

import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 데이터베이스 저장 공간을 지킨다. Railway 체험 요금제는 볼륨이 0.5GB라, 꽉 차면 회원가입·노트 저장까지 실패한다.
 * <ul>
 *   <li>사용량이 기준(softLimitMb)을 넘으면 백필이 새 자료를 쓰지 않고 멈춘다.</li>
 *   <li>보관 기간보다 오래된 주가는 지운다. 지울 양이 많으면 TRUNCATE로 한 번에 비워 디스크를 바로 돌려받고, 보관 기간만 다시 받는다.</li>
 * </ul>
 * 크기 확인은 PostgreSQL에서만 하고(H2 테스트에서는 null), 실패해도 서비스는 멈추지 않는다.
 */
@Component
public class StockStorage {
    private static final Logger log = LoggerFactory.getLogger(StockStorage.class);
    private final JdbcTemplate jdbc;
    private final long softLimitBytes;
    private final Clock clock;
    private Long cachedBytes;
    private Instant cachedAt = Instant.EPOCH;

    @Autowired
    public StockStorage(JdbcTemplate jdbc, @Value("${aira.stock.storage.soft-limit-mb:430}") long softLimitMb) {
        this(jdbc, softLimitMb, Clock.systemUTC());
    }

    StockStorage(JdbcTemplate jdbc, long softLimitMb, Clock clock) {
        this.jdbc = jdbc; this.softLimitBytes = softLimitMb * 1024 * 1024; this.clock = clock;
    }

    public long softLimitMb() { return softLimitBytes / 1024 / 1024; }

    /** 데이터베이스 전체 크기(바이트). PostgreSQL이 아니거나 확인에 실패하면 null. 1분 동안 같은 값을 쓴다. */
    public synchronized Long databaseBytes() {
        Instant now = clock.instant();
        if (cachedBytes != null && cachedAt.plus(Duration.ofMinutes(1)).isAfter(now)) return cachedBytes;
        try {
            cachedBytes = jdbc.queryForObject("SELECT pg_database_size(current_database())", Long.class);
        } catch (RuntimeException e) {
            cachedBytes = null;
        }
        cachedAt = now;
        return cachedBytes;
    }

    /** 기준을 넘었으면 true. 크기를 모르면(H2 등) false. */
    public boolean nearlyFull() {
        Long bytes = databaseBytes();
        return softLimitBytes > 0 && bytes != null && bytes >= softLimitBytes;
    }

    synchronized void forget() { cachedBytes = null; }

    /** 큰 표 순서로 크기(MB). 운영 확인용. */
    public List<Map<String, Object>> largestTables(int limit) {
        try {
            return jdbc.queryForList("SELECT relname AS name, ROUND(pg_total_relation_size(relid) / 1048576.0, 1) AS mb "
                    + "FROM pg_catalog.pg_statio_user_tables ORDER BY pg_total_relation_size(relid) DESC LIMIT ?", limit);
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * keepFrom보다 오래된 주가를 지운다. 오래된 줄이 전체의 30% 이상이면 표를 통째로 비운다(TRUNCATE는 디스크를 바로 돌려준다).
     * 그 경우 받은 날짜 기록도 비워서 보관 기간만큼 다시 받게 한다. 반환: 지운 줄 수(통째로 비웠으면 -1).
     */
    public long trimPrices(LocalDate keepFrom) {
        Long old = jdbc.queryForObject("SELECT COUNT(*) FROM stock_price_daily WHERE trading_date < ?", Long.class, Date.valueOf(keepFrom));
        if (old == null || old == 0) return 0;
        Long all = jdbc.queryForObject("SELECT COUNT(*) FROM stock_price_daily", Long.class);
        if (all != null && old * 10 >= all * 3) {
            jdbc.execute("TRUNCATE TABLE stock_price_daily");
            jdbc.execute("TRUNCATE TABLE stock_price_day_loaded");
            log.warn("Stock price table emptied to free disk ({} of {} rows were older than {}); refilling the kept period", old, all, keepFrom);
            forget();
            return -1;
        }
        int removed = jdbc.update("DELETE FROM stock_price_daily WHERE trading_date < ?", Date.valueOf(keepFrom));
        jdbc.update("DELETE FROM stock_price_day_loaded WHERE trading_date < ?", Date.valueOf(keepFrom));
        forget();
        return removed;
    }

    public Map<String, Object> report() {
        Map<String, Object> out = new LinkedHashMap<>();
        Long bytes = databaseBytes();
        out.put("databaseMb", bytes == null ? null : Math.round(bytes / 1048576.0));
        out.put("softLimitMb", softLimitMb());
        out.put("nearlyFull", nearlyFull());
        out.put("largest", largestTables(8));
        return out;
    }
}
