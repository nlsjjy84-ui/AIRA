package com.aira.api.stock;

import com.aira.api.market.krx.KrxClient;
import com.aira.api.market.krx.KrxDataset;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 종목 화면이 비지 않도록 과거 자료를 채운다.
 * <ul>
 *   <li>주가: KRX 일별 매매정보를 날짜별로 받아 최근 1년치(코스피·코스닥 전 종목)를 저장한다. 이미 받은 날은 건너뛴다.</li>
 *   <li>재무: OpenDART 다중회사 주요계정(사업보고서)으로 회사 100곳씩, 최근 6개 사업연도를 저장한다.</li>
 * </ul>
 * 서버가 뜬 뒤 별도 스레드에서 돌고, 인증키가 없으면 아무것도 하지 않는다. 한 날짜·한 묶음이 실패해도 다음으로 넘어간다.
 */
@Component
@org.springframework.core.annotation.Order(1)
public class StockHistoryBackfill implements org.springframework.boot.ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StockHistoryBackfill.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final KrxClient krx;
    private final StockPriceStore prices;
    private final DartStockClient dart;
    private final FinancialHistoryStore financials;
    private final StockCompanyLookup companies;
    private ProfileStore profiles;
    private PriceAlertService alerts;
    private StockStorage storage;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setStorage(StockStorage storage) { this.storage = storage; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setAlerts(PriceAlertService alerts) { this.alerts = alerts; }
    private final boolean enabled;
    private final String krxKey;
    private final int priceDays;
    private final long pauseMillis;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final java.util.Map<String, Object> status = new java.util.concurrent.ConcurrentHashMap<>();

    /** 운영 확인용: 지금 어느 단계인지, 몇 개를 받았는지, 마지막 오류가 무엇인지. */
    public java.util.Map<String, Object> status() {
        var out = new java.util.TreeMap<String, Object>(status);
        out.put("running", running.get());
        out.put("enabled", enabled);
        out.put("priceDays", priceDays);
        if (storage != null) out.put("storage", storage.report());
        return out;
    }

    private void note(String key, Object value) { if (value != null) status.put(key, value); }

    /** 저장 공간이 기준을 넘었으면 새 자료를 쓰지 않는다. */
    private boolean storageFull(String phase) {
        if (storage == null || !storage.nearlyFull()) return false;
        note("paused", phase + " — database is near its size limit (" + storage.softLimitMb() + "MB)");
        log.warn("Stock backfill {} paused: database is near its size limit", phase);
        return true;
    }

    @Autowired
    public StockHistoryBackfill(KrxClient krx, StockPriceStore prices, DartStockClient dart, FinancialHistoryStore financials,
            StockCompanyLookup companies,
            @Value("${aira.stock.backfill.enabled:false}") boolean enabled,
            @Value("${aira.krx.auth-key:}") String krxKey,
            @Value("${aira.stock.backfill.price-days:370}") int priceDays,
            @Value("${aira.stock.backfill.pause-millis:250}") long pauseMillis) {
        this(krx, prices, dart, financials, companies, enabled, krxKey, priceDays, pauseMillis, Clock.systemUTC());
    }

    StockHistoryBackfill(KrxClient krx, StockPriceStore prices, DartStockClient dart, FinancialHistoryStore financials,
            StockCompanyLookup companies, boolean enabled, String krxKey, int priceDays, long pauseMillis, Clock clock) {
        this.krx = krx; this.prices = prices; this.dart = dart; this.financials = financials; this.companies = companies;
        this.enabled = enabled; this.krxKey = krxKey == null ? "" : krxKey; this.priceDays = priceDays;
        this.pauseMillis = pauseMillis; this.clock = clock;
    }

    /** 다른 시작 작업(전 종목 준비 등)이 오래 걸려도 기다리지 않도록, 표를 만든 바로 다음에 별도 스레드로 시작한다. */
    @Override
    public void run(org.springframework.boot.ApplicationArguments args) { onReady(); }

    public void onReady() {
        if (!enabled) return;
        Thread worker = new Thread(this::runOnce, "stock-history-backfill");
        worker.setDaemon(true);
        worker.start();
    }

    // 하루 네 번(08:40 · 12:40 · 15:40 · 18:40): 아침 거래소 자료, 낮, 장 마감 직후, 저녁 공시까지 고르게 받는다.
    @Scheduled(cron = "${aira.stock.backfill.cron:0 40 8,12,15,18 * * *}", zone = "Asia/Seoul")
    public void daily() {
        if (enabled) runOnce();
    }

    void runOnce() {
        if (!running.compareAndSet(false, true)) return;
        note("startedAt", java.time.Instant.now().toString());
        status.remove("paused");
        try {
            if (storage != null) {
                try {
                    long trimmed = storage.trimPrices(LocalDate.now(clock.withZone(KST)).minusDays(priceDays));
                    if (trimmed != 0) note("prices.trimmed", trimmed < 0 ? "emptied and refilling " + priceDays + " days" : trimmed + " rows");
                } catch (RuntimeException e) {
                    note("prices.trimError", e.getMessage());
                }
            }
            if (!krxKey.isBlank()) {
                for (String market : List.of("KOSPI", "KOSDAQ")) backfillPrices(market);
                for (String market : List.of("KOSPI", "KOSDAQ")) backfillIndex(market);
                if (alerts != null) {
                    try { note("alerts.triggered", alerts.evaluate()); } catch (RuntimeException e) { note("alerts.lastError", e.getMessage()); }
                }
            } else {
                log.warn("Stock price backfill skipped: AIRA_KRX_AUTH_KEY is not configured");
            }
            if (dart.configured()) { backfillFinancials(); backfillQuarters(); backfillProfiles(); }
            else log.warn("Financial history backfill skipped: OPENDART_API_KEY is not configured");
        } catch (RuntimeException e) {
            log.error("Stock history backfill stopped", e);
        } finally {
            note("finishedAt", java.time.Instant.now().toString());
            note("phase", "done");
            running.set(false);
        }
    }

    int backfillPrices(String market) {
        KrxDataset dataset = "KOSPI".equals(market) ? KrxDataset.STK_DAILY : KrxDataset.KSQ_DAILY;
        note("phase", "prices " + market);
        LocalDate today = LocalDate.now(clock.withZone(KST));
        LocalDate from = today.minusDays(priceDays);
        Set<LocalDate> done = prices.loadedDays(market, from);
        int loaded = 0, failures = 0;
        for (LocalDate date = today; !date.isBefore(from); date = date.minusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            if (done.contains(date)) continue;
            if (storageFull("prices " + market)) break;
            try {
                List<Map<String, String>> rows = krx.fetch(dataset, date).rows();
                // 오늘·어제 자료는 아직 안 올라왔을 수 있으므로 비어 있으면 "받음"으로 표시하지 않는다.
                if (rows.isEmpty() && !date.isBefore(today.minusDays(1))) continue;
                prices.saveDay(market, date, KrxDailyRows.convert(market, date, rows));
                loaded++;
                note("prices." + market + ".lastDate", date.toString());
                note("prices." + market + ".loaded", loaded);
            } catch (RuntimeException e) {
                failures++;
                log.warn("KRX daily backfill failed for {} {}: {}", market, date, e.getMessage());
                note("prices." + market + ".lastError", date + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
                note("prices." + market + ".failures", failures);
                if (failures >= 15) { log.warn("KRX daily backfill paused after repeated failures"); break; }
            }
            pause();
        }
        log.info("Stock price backfill {} complete: {} new trading days", market, loaded);
        return loaded;
    }

    /** 종목과 시장을 같은 기간으로 비교할 수 있도록 코스피·코스닥 대표지수도 같은 표에 "KOSPI"/"KOSDAQ" 코드로 저장한다. */
    int backfillIndex(String market) {
        KrxDataset dataset = "KOSPI".equals(market) ? KrxDataset.KOSPI_INDEX : KrxDataset.KOSDAQ_INDEX;
        String store = "IDX_" + market;
        note("phase", "index " + market);
        LocalDate today = LocalDate.now(clock.withZone(KST));
        LocalDate from = today.minusDays(priceDays);
        Set<LocalDate> done = prices.loadedDays(store, from);
        int loaded = 0, failures = 0;
        for (LocalDate date = today; !date.isBefore(from); date = date.minusDays(1)) {
            if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) continue;
            if (done.contains(date)) continue;
            if (storageFull("index " + market)) break;
            try {
                List<Map<String, String>> rows = krx.fetch(dataset, date).rows();
                if (rows.isEmpty() && !date.isBefore(today.minusDays(1))) continue;
                prices.saveDay(store, date, KrxDailyRows.convertIndex(market, store, date, rows));
                loaded++;
                note("index." + market + ".loaded", loaded);
            } catch (RuntimeException e) {
                failures++;
                log.warn("KRX index backfill failed for {} {}: {}", market, date, e.getMessage());
                note("index." + market + ".lastError", date + " " + e.getMessage());
                if (failures >= 15) break;
            }
            pause();
        }
        log.info("Index backfill {} complete: {} new trading days", market, loaded);
        return loaded;
    }

    int backfillFinancials() {
        List<String> codes = companies.allCorpCodes();
        if (codes.isEmpty()) return 0;
        int latest = LocalDate.now(clock.withZone(KST)).getYear() - 1;
        int saved = 0;
        note("phase", "financials");
        // 한 번의 사업보고서에 당기·전기·전전기가 함께 있으므로 3년 간격 두 번이면 6개 사업연도가 된다.
        for (int reportYear : List.of(latest, latest - 3, latest - 6, latest - 9)) {
            Set<String> doneChunks = financials.loadedChunks(reportYear);
            for (int i = 0; i < codes.size(); i += 100) {
                List<String> chunk = new ArrayList<>(codes.subList(i, Math.min(codes.size(), i + 100)));
                if (doneChunks.contains(chunk.get(0))) continue;
                if (storageFull("financials")) return saved;
                try {
                    var rows = FinancialHistoryRows.convert(dart.multiAccounts(chunk, reportYear), reportYear);
                    financials.saveChunk(reportYear, chunk.get(0), rows);
                    saved += rows.size();
                    note("financials.saved", saved);
                } catch (RuntimeException e) {
                    log.warn("OpenDART financial history failed for {} chunk {}: {}", reportYear, chunk.get(0), e.getMessage());
                    note("financials.lastError", reportYear + " " + chunk.get(0) + " " + e.getMessage());
                }
                pause();
            }
        }
        log.info("Financial history backfill complete: {} values", saved);
        return saved;
    }

    /** 분기 재무상태표(부채·자본 등)를 함께 저장하기 시작한 시점. 이보다 먼저 받은 묶음은 한 번 더 받는다. */
    static final java.time.Instant QUARTER_BALANCE_SINCE = java.time.Instant.parse("2026-10-16T15:00:00Z");

    /** 분기 실적: 올해와 지난 2년의 1분기·반기·3분기 보고서. 아직 나오지 않은 보고서는 다음에 다시 시도한다. */
    int backfillQuarters() {
        List<String> codes = companies.allCorpCodes();
        if (codes.isEmpty()) return 0;
        LocalDate today = LocalDate.now(clock.withZone(KST));
        int saved = 0;
        note("phase", "quarters");
        for (int year = today.getYear(); year >= today.getYear() - 2; year--) {
            for (String[] report : new String[][]{{"Q1", "11013"}, {"H1", "11012"}, {"Q3", "11014"}}) {
                // 보고서 제출 기한(분기 말 + 45일) 전이면 묻지 않는다.
                LocalDate due = LocalDate.of(year, switch (report[0]) { case "Q1" -> 5; case "H1" -> 8; default -> 11; }, 14);
                if (today.isBefore(due)) continue;
                Set<String> done = financials.loadedQuarterChunks(year, report[0], QUARTER_BALANCE_SINCE);
                for (int i = 0; i < codes.size(); i += 100) {
                    List<String> chunk = new ArrayList<>(codes.subList(i, Math.min(codes.size(), i + 100)));
                    if (done.contains(chunk.get(0))) continue;
                    if (storageFull("quarters")) return saved;
                    try {
                        var rows = FinancialHistoryRows.cumulative(dart.multiAccounts(chunk, year, report[1]), year, report[0]);
                        financials.saveQuarterChunk(year, report[0], chunk.get(0), rows);
                        saved += rows.size();
                    } catch (RuntimeException e) {
                        log.warn("OpenDART quarterly failed for {} {} chunk {}: {}", year, report[0], chunk.get(0), e.getMessage());
                        note("quarters.lastError", year + report[0] + " " + e.getMessage());
                    }
                    pause();
                }
            }
        }
        log.info("Quarterly backfill complete: {} values", saved);
        return saved;
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setProfiles(ProfileStore profiles) { this.profiles = profiles; }

    /** 같은 업종 비교를 위해 전 회사의 기업개황(업종코드)을 30일마다 한 번 받는다. */
    int backfillProfiles() {
        if (profiles == null) return 0;
        java.time.Instant now = clock.instant();
        Set<String> fresh = profiles.freshCorpCodes(now.minus(java.time.Duration.ofDays(30)));
        note("phase", "profiles");
        int saved = 0, failures = 0;
        for (String corpCode : companies.allCorpCodes()) {
            if (fresh.contains(corpCode)) continue;
            if (storageFull("profiles")) break;
            try {
                var payload = dart.company(corpCode);
                if (!payload.isEmpty()) { profiles.save(corpCode, payload, clock.instant()); saved++; if (saved % 50 == 0) note("profiles.saved", saved); }
            } catch (RuntimeException e) {
                failures++;
                note("profiles.lastError", e.getMessage());
                if (failures >= 30) { log.warn("Company profile backfill paused after repeated failures: {}", e.getMessage()); break; }
            }
            pause();
        }
        log.info("Company profile backfill complete: {} profiles", saved);
        return saved;
    }

    private void pause() {
        if (pauseMillis <= 0) return;
        try { Thread.sleep(pauseMillis); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
