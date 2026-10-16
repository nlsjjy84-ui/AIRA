package com.aira.api.demo;

import com.aira.api.market.opendart.OfficialFactEnrichmentOperation;
import com.aira.api.market.opendart.PriorYearBackfill;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 이미 저장된 회사에 추가 공식 항목(유동자산·유동부채·현금성자산·현금흐름 3종)을 천천히 채운다.
 * 전체 상장사 수집이 켜져 있을 때만 함께 켜지고, DART 하루 호출 한도를 아끼려고 적은 수를 긴 간격으로 처리한다.
 * 연도마다 채울 회사가 없어지면 다음 연도로 넘어가고, 모두 끝나면 길게 쉰다. aira.opendart.enrich.years 를 비우면 하지 않는다.
 */
@Component
@ConditionalOnProperty(name = "aira.opendart.all-companies.enabled", havingValue = "true")
public class ScheduledFactEnrichment {
    private static final Logger log = LoggerFactory.getLogger(ScheduledFactEnrichment.class);

    private final OfficialFactEnrichmentOperation operation;
    private final String mode;
    private final String apiKey;
    private final List<Integer> years;
    private final int batchSize;
    private final long pauseMillis;
    private final Duration pauseAfterStop;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<String> skipped = ConcurrentHashMap.newKeySet();
    private volatile int yearIndex = 0;
    private volatile Instant pausedUntil = Instant.EPOCH;

    @Autowired
    public ScheduledFactEnrichment(OfficialFactEnrichmentOperation operation,
            @Value("${aira.demo.bootstrap.mode:}") String mode,
            @Value("${aira.opendart.api-key:}") String apiKey,
            @Value("${aira.opendart.enrich.years:2025,2024}") String years,
            @Value("${aira.opendart.enrich.batch-size:30}") int batchSize,
            @Value("${aira.opendart.enrich.pause-ms:400}") long pauseMillis,
            @Value("${aira.opendart.enrich.pause-hours:12}") long pauseHours) {
        this(operation, mode, apiKey, parseYears(years), batchSize, pauseMillis, Duration.ofHours(pauseHours),
                Executors.newSingleThreadExecutor(runnable -> {
                    var thread = new Thread(runnable, "opendart-enrich");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    ScheduledFactEnrichment(OfficialFactEnrichmentOperation operation, String mode, String apiKey, List<Integer> years,
            int batchSize, long pauseMillis, Duration pauseAfterStop, Executor executor) {
        this.operation = operation;
        this.mode = mode;
        this.apiKey = apiKey;
        this.years = List.copyOf(years);
        this.batchSize = batchSize;
        this.pauseMillis = pauseMillis;
        this.pauseAfterStop = pauseAfterStop;
        this.executor = executor;
    }

    static List<Integer> parseYears(String value) {
        List<Integer> out = new ArrayList<>();
        if (value == null) return out;
        for (String part : value.split(",")) {
            String trimmed = part.strip();
            if (trimmed.isEmpty()) continue;
            try {
                int year = Integer.parseInt(trimmed);
                if (year >= 2000 && year <= 9999) out.add(year);
            } catch (NumberFormatException ignored) {
                // 잘못 적은 연도는 건너뛴다.
            }
        }
        return out;
    }

    @Scheduled(initialDelayString = "${aira.opendart.enrich.initial-delay-ms:900000}",
            fixedDelayString = "${aira.opendart.enrich.delay-ms:600000}")
    public void tick() {
        if (years.isEmpty() || !"live".equals(mode) || apiKey == null || apiKey.isBlank()) return;
        if (Instant.now().isBefore(pausedUntil)) return;
        if (!running.compareAndSet(false, true)) return;
        executor.execute(this::runBatch);
    }

    void runBatch() {
        try {
            if (yearIndex >= years.size()) {
                yearIndex = 0;
                skipped.clear();
                pausedUntil = Instant.now().plus(pauseAfterStop);
                log.info("OpenDART enrichment finished all years; next pass after {}", pauseAfterStop);
                return;
            }
            int year = years.get(yearIndex);
            var result = operation.run(year, batchSize, pauseMillis, skipped);
            log.info("OpenDART enrichment {}: pending={}, attempted={}, prepared={}, noExtra={}, failed={}, stop={}",
                    year, result.pendingBefore(), result.attempted(), result.prepared(), result.noData(), result.failed(),
                    result.stopReason());
            if (result.attempted() == 0) {
                yearIndex++;
            } else if (result.stopReason() != PriorYearBackfill.StopReason.NONE) {
                pausedUntil = Instant.now().plus(pauseAfterStop);
                log.warn("OpenDART enrichment paused until {} ({})", pausedUntil, result.stopReason());
            }
        } catch (RuntimeException e) {
            pausedUntil = Instant.now().plus(Duration.ofMinutes(30));
            log.error("OpenDART enrichment failed; retrying after 30 minutes", e);
        } finally {
            running.set(false);
        }
    }
}
