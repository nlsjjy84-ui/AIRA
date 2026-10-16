package com.aira.api.stock;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자료 상태: 공식 자료마다 "어느 날짜(기간)까지의 자료인지"와 "AIRA가 언제 마지막으로 가져왔는지"를 따로 보여 준다.
 *
 * <p>DATA_FRESHNESS_BOUNDARY_V1 원칙대로 두 시각을 섞지 않는다. 옛 자료를 오늘 다시 가져왔다고 "최신"이라 하지 않고,
 * 매일 들어와야 하는 자료(주가·지수·뉴스)가 정해진 시간보다 오래 멈춰 있으면 '늦음'으로 표시해 운영자가 바로 알 수 있게 한다.
 * 연간·분기 자료(재무·GDP)는 발표 주기가 길어 날짜만 보여 주고 늦음 판정은 하지 않는다.</p>
 */
@RestController
public class DataStatusController {
    private static final Logger log = LoggerFactory.getLogger(DataStatusController.class);
    static final ZoneId KST = ZoneId.of("Asia/Seoul");

    /** 매일 들어오는 자료: 마지막 수집이 이보다 오래되면 자동 수집이 멈춘 것으로 본다. */
    static final Duration DAILY_COLLECTION_LIMIT = Duration.ofHours(36);
    /** 거래일 자료: 주말·연휴를 감안해 기준일이 이보다 오래되면 늦음으로 본다(시장 지수 화면과 같은 5일). */
    static final int TRADING_DAY_LIMIT_DAYS = 5;

    public record Source(String key, String label, String provider, String cadence,
            String observedThrough, Instant collectedAt, String state, String note) {}

    public record Response(Instant checkedAt, String overall, List<Source> sources) {}

    private final JdbcTemplate jdbc;
    private final Clock clock;

    // 생성자는 하나만 둔다(스프링이 생성자를 고를 수 있게). 판정 규칙은 judgeDaily 로 따로 시험한다.
    public DataStatusController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.clock = Clock.systemUTC();
    }

    @GetMapping("/api/stocks/data-status")
    public Response status() {
        Instant now = clock.instant();
        List<Source> sources = new ArrayList<>();
        sources.add(daily("STOCK_PRICE", "종목 주가", "KRX", true,
                "SELECT max(trading_date) AS observed, max(loaded_at) AS collected FROM stock_price_day_loaded", now));
        sources.add(daily("MARKET_INDEX", "시장 지수(KOSPI·KOSDAQ)", "KRX", true, factSql("'INDEX_CLOSE'"), now));
        sources.add(daily("MARKET_NEWS", "시장 뉴스(참고)", "뉴스 검색", false,
                "SELECT NULL AS observed, max(fetched_at) AS collected FROM market_news_cache", now));
        sources.add(periodic("COMPANY_FINANCIALS", "회사 공식 재무(연결·연간)", "OpenDART", "사업보고서가 나올 때(연 1회)",
                factSql("'REVENUE','OPERATING_INCOME','NET_INCOME','TOTAL_ASSETS','TOTAL_LIABILITIES','TOTAL_EQUITY'")));
        sources.add(periodic("FINANCIAL_HISTORY", "10년 실적표", "OpenDART", "사업보고서가 나올 때(연 1회)",
                "SELECT CAST(max(report_year) AS varchar) AS observed, max(loaded_at) AS collected FROM company_financial_history_loaded"));
        sources.add(periodic("REAL_GDP", "실질 GDP", "한국은행 ECOS", "분기마다", factSql("'REAL_GDP'")));
        String overall = sources.stream().anyMatch(s -> s.state().equals("LATE")) ? "LATE"
                : sources.stream().allMatch(s -> s.state().equals("UNKNOWN")) ? "UNKNOWN" : "OK";
        return new Response(now, overall, sources);
    }

    private static String factSql(String predicates) {
        return "SELECT CAST(max(f.period_end) AS varchar) AS observed, max(ev.collected_at) AS collected "
                + "FROM fact f JOIN fact_assertion fa ON fa.fact_id = f.id JOIN evidence ev ON ev.id = fa.evidence_id "
                + "WHERE f.status = 'SUPPORTED' AND f.predicate IN (" + predicates + ")";
    }

    private Source daily(String key, String label, String provider, boolean tradingDay, String sql, Instant now) {
        Row row = read(key, sql);
        if (row == null) return unknown(key, label, provider, "매일");
        return judgeDaily(key, label, provider, tradingDay, row.observed(), row.collected(), now);
    }

    private Source periodic(String key, String label, String provider, String cadence, String sql) {
        Row row = read(key, sql);
        if (row == null) return unknown(key, label, provider, cadence);
        if (row.observed() == null && row.collected() == null) {
            return new Source(key, label, provider, cadence, null, null, "UNKNOWN", "아직 저장된 자료가 없습니다.");
        }
        return new Source(key, label, provider, cadence, row.observed(), row.collected(), "OK",
                "발표 주기가 길어 날짜만 보여 드려요. 새 보고서가 나오면 자동으로 더해집니다.");
    }

    /** 매일 자료의 상태 판정. 테스트에서 직접 부른다. */
    static Source judgeDaily(String key, String label, String provider, boolean tradingDay,
            String observed, Instant collected, Instant now) {
        if (observed == null && collected == null) {
            return new Source(key, label, provider, "매일", null, null, "UNKNOWN", "아직 저장된 자료가 없습니다.");
        }
        List<String> problems = new ArrayList<>();
        if (collected != null && Duration.between(collected, now).compareTo(DAILY_COLLECTION_LIMIT) > 0) {
            problems.add("마지막 수집이 " + Duration.between(collected, now).toHours() + "시간 전이라 자동 수집이 멈췄을 수 있어요");
        }
        if (tradingDay && observed != null) {
            LocalDate day = LocalDate.parse(observed.substring(0, 10));
            long behind = day.until(LocalDate.ofInstant(now, KST), java.time.temporal.ChronoUnit.DAYS);
            if (behind >= TRADING_DAY_LIMIT_DAYS) problems.add("가장 최근 거래일 자료가 " + behind + "일 전 것이에요");
        }
        if (problems.isEmpty()) {
            return new Source(key, label, provider, "매일", observed, collected, "OK",
                    tradingDay ? "장이 끝난 뒤 하루 한 번 공식 자료로 바뀌어요. 실시간 시세가 아닙니다." : "하루에 여러 번 새로 가져와요.");
        }
        return new Source(key, label, provider, "매일", observed, collected, "LATE", String.join(" · ", problems) + ".");
    }

    private static Source unknown(String key, String label, String provider, String cadence) {
        return new Source(key, label, provider, cadence, null, null, "UNKNOWN", "지금은 상태를 확인할 수 없습니다.");
    }

    private record Row(String observed, Instant collected) {}

    private Row read(String key, String sql) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(sql);
            if (rows.isEmpty()) return new Row(null, null);
            Map<String, Object> row = rows.get(0);
            Object observed = value(row, "observed");
            Object collected = value(row, "collected");
            return new Row(observed == null ? null : observed.toString(), toInstant(collected));
        } catch (RuntimeException e) {
            // 표가 아직 없거나(새 DB) 읽기에 실패해도 다른 자료 상태는 보여 준다.
            log.warn("data status {} could not be read: {}", key, e.getClass().getSimpleName());
            return null;
        }
    }

    private static Object value(Map<String, Object> row, String name) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        }
        return null;
    }

    private static Instant toInstant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant instant) return instant;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (value instanceof java.time.ZonedDateTime zdt) return zdt.toInstant();
        if (value instanceof java.time.LocalDateTime ldt) return ldt.atZone(KST).toInstant();
        return null;
    }
}
