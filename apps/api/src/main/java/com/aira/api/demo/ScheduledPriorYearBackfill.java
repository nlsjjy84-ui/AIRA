package com.aira.api.demo;

import com.aira.api.market.opendart.PriorYearBackfill;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
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
 * 전체 상장사 수집({@link ScheduledAllCompaniesRefresh})이 켜져 있으면 함께 켜져, 이전 사업연도(기본 2024, 2023)를
 * 천천히 채운다. DART 하루 호출 한도를 아끼려고 한 번에 적은 수만, 긴 간격으로 처리한다.
 * aira.opendart.backfill.years 를 비우면 아무것도 하지 않는다.
 */
@Component
@ConditionalOnProperty(name = "aira.opendart.all-companies.enabled", havingValue = "true")
public class ScheduledPriorYearBackfill {
    private static final Logger log = LoggerFactory.getLogger(ScheduledPriorYearBackfill.class);

    private final PriorYearBackfill operation;
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
    public ScheduledPriorYearBackfill(PriorYearBackfill operation,
            @Value("${aira.demo.bootstrap.mode:}") String mode,
            @Value("${aira.opendart.api-key:}") String apiKey,
            @Value("${aira.opendart.backfill.years:2024,2023}") String years,
            @Value("${aira.opendart.backfill.batch-size:40}") int batchSize,
            @Value("${aira.opendart.backfill.pause-ms:300}") long pauseMillis,
            @Value("${aira.opendart.backfill.pause-hours:12}") long pauseHours) {
        this(operation, mode, apiKey, parseYears(years), batchSize, pauseMillis, Duration.ofHours(pauseHours),
                Executors.newSingleThreadExecutor(runnable -> {
                    var thread = new Thread(runnable, "opendart-backfill");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    ScheduledPriorYearBackfill(PriorYearBackfill operation, String mode, String apiKey, List<Integer> years,
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

    static List<Integer> parseYears(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.split(",")).map(String::trim).filter(part -> part.matches("[0-9]{4}"))
                .map(Integer::valueOf).distinct().toList();
    }

    @Scheduled(initialDelayString = "${aira.opendart.backfill.initial-delay-ms:600000}",
            fixedDelayString = "${aira.opendart.backfill.delay-ms:900000}")
    public void tick() {
        if (years.isEmpty() || !"live".equals(mode) || apiKey == null || apiKey.isBlank()) {
            return;
        }
        if (Instant.now().isBefore(pausedUntil)) {
            return;
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }
        executor.execute(this::runBatch);
    }

    void runBatch() {
        try {
            if (yearIndex >= years.size()) {
                yearIndex = 0;
                skipped.clear();
                pausedUntil = Instant.now().plus(pauseAfterStop);
                return;
            }
            int year = years.get(yearIndex);
            var result = operation.run(year, batchSize, pauseMillis, skipped);
            log.info(result.summary(year));
            if (result.attempted() == 0) {
                yearIndex++;
                skipped.clear();
            } else if (result.stopReason() != PriorYearBackfill.StopReason.NONE) {
                pausedUntil = Instant.now().plus(pauseAfterStop);
                log.warn("OpenDART prior-year backfill paused until {} ({})", pausedUntil, result.stopReason());
            }
        } catch (RuntimeException e) {
            pausedUntil = Instant.now().plus(Duration.ofMinutes(30));
            log.error("OpenDART prior-year backfill failed; retrying after 30 minutes", e);
        } finally {
            running.set(false);
        }
    }
}
