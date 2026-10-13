package com.aira.api.assist;

import com.aira.api.assist.ClaimDtos.ClaimResult;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * "이 말, 사실이야?" 판정 통계. 문장·사용자·회사는 남기지 않고 날짜·판정·항목별 개수만 센다(개인정보 원칙).
 * 첫 화면에 "확인한 말 N건 중 M건이 공식 값과 달랐다"처럼 AIRA만 보여 줄 수 있는 숫자를 내기 위한 것이다.
 */
@RestController
public class ClaimStats {
    private static final Logger log = LoggerFactory.getLogger(ClaimStats.class);
    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final List<String> VERDICTS = List.of("MATCH", "APPROX", "MISMATCH", "NO_DATA", "NOT_CHECKABLE");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    /** 공식 집계 시작일. 판정 규칙을 고친(2026-10-13) 뒤부터 센다 — 고치기 전 실험 판정이 첫 화면 숫자에 섞이지 않게. */
    static final LocalDate COUNT_FROM = LocalDate.of(2026, 10, 14);
    private final LocalDate countFrom;

    @org.springframework.beans.factory.annotation.Autowired
    public ClaimStats(JdbcTemplate jdbc) { this(jdbc, Clock.systemUTC(), COUNT_FROM); }

    ClaimStats(JdbcTemplate jdbc, Clock clock) { this(jdbc, clock, null); }

    ClaimStats(JdbcTemplate jdbc, Clock clock, LocalDate countFrom) { this.jdbc = jdbc; this.clock = clock; this.countFrom = countFrom; }

    /** 판정 결과 개수를 더한다. 통계가 실패해도 사실 확인 자체는 막지 않는다. */
    public void record(List<ClaimResult> results) {
        if (results == null || results.isEmpty()) return;
        LocalDate today = LocalDate.now(clock.withZone(KST));
        for (ClaimResult result : results) {
            String verdict = VERDICTS.contains(result.verdict()) ? result.verdict() : "NOT_CHECKABLE";
            String item = result.item() == null || result.item().isBlank() ? "-" : result.item().strip();
            if (item.length() > 40) item = item.substring(0, 40);
            try {
                int updated = jdbc.update("UPDATE claim_check_stat SET checks = checks + 1 WHERE stat_date=? AND verdict=? AND item=?",
                        Date.valueOf(today), verdict, item);
                if (updated == 0) {
                    try {
                        jdbc.update("INSERT INTO claim_check_stat(stat_date, verdict, item, checks) VALUES(?,?,?,1)", Date.valueOf(today), verdict, item);
                    } catch (org.springframework.dao.DuplicateKeyException raced) {
                        jdbc.update("UPDATE claim_check_stat SET checks = checks + 1 WHERE stat_date=? AND verdict=? AND item=?",
                                Date.valueOf(today), verdict, item);
                    }
                }
            } catch (RuntimeException failure) {
                log.warn("claim stat not recorded: {}", failure.getClass().getSimpleName());
            }
        }
    }

    /** 최근 N일 판정 통계(공개). 항목별로 몇 번 확인했고 그중 몇 번이 공식 값과 달랐는지. */
    @GetMapping("/api/stocks/claim-stats")
    public Map<String, Object> summary(@RequestParam(defaultValue = "30") int days) {
        int window = Math.max(1, Math.min(days, 365));
        LocalDate today = LocalDate.now(clock.withZone(KST));
        LocalDate since = today.minusDays(window - 1L);
        if (countFrom != null && since.isBefore(countFrom)) since = countFrom;
        Map<String, Integer> byVerdict = new LinkedHashMap<>();
        for (String verdict : VERDICTS) byVerdict.put(verdict, 0);
        Map<String, int[]> byItem = new LinkedHashMap<>();
        try {
            jdbc.query("SELECT verdict, item, sum(checks) FROM claim_check_stat WHERE stat_date >= ? GROUP BY verdict, item",
                    rs -> {
                        String verdict = rs.getString(1);
                        String item = rs.getString(2);
                        int count = rs.getInt(3);
                        byVerdict.merge(verdict, count, Integer::sum);
                        if (!"-".equals(item)) {
                            int[] pair = byItem.computeIfAbsent(item, k -> new int[2]);
                            pair[0] += count;
                            if ("MISMATCH".equals(verdict)) pair[1] += count;
                        }
                    }, Date.valueOf(since));
        } catch (RuntimeException failure) {
            log.warn("claim stats unavailable: {}", failure.getClass().getSimpleName());
        }
        int total = byVerdict.values().stream().mapToInt(Integer::intValue).sum();
        int checkable = byVerdict.get("MATCH") + byVerdict.get("APPROX") + byVerdict.get("MISMATCH");
        List<Map<String, Object>> items = byItem.entrySet().stream()
                .sorted((a, b) -> b.getValue()[0] - a.getValue()[0])
                .map(e -> Map.<String, Object>of("item", e.getKey(), "checks", e.getValue()[0], "mismatches", e.getValue()[1]))
                .toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("since", since.toString());
        out.put("until", today.toString());
        out.put("total", total);
        out.put("checkable", checkable);
        out.put("mismatches", byVerdict.get("MISMATCH"));
        out.put("byVerdict", byVerdict);
        out.put("byItem", items);
        return out;
    }
}
