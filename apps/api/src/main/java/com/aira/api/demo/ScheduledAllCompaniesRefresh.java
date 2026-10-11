package com.aira.api.demo;

import com.aira.api.market.opendart.OfficialAllCompaniesPreparationOperation;
import java.time.Duration;
import java.time.Instant;
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
 * 상장사 전체의 연결 연간 재무를 공식 공시에서 조금씩 채운다. aira.opendart.all-companies.enabled=true 일 때만 켜진다.
 * 한 번에 {@code batch-size}곳씩 처리하고 {@code delay-ms}마다 이어서 한다. 한도 초과·인증 오류로 멈추면
 * {@code pause-hours} 동안 쉰다. 오래 걸리는 일이라 예약 실행의 공용 스레드를 막지 않도록 전용 스레드에서 돌린다.
 */
@Component
@ConditionalOnProperty(name = "aira.opendart.all-companies.enabled", havingValue = "true")
public class ScheduledAllCompaniesRefresh {
    private static final Logger log = LoggerFactory.getLogger(ScheduledAllCompaniesRefresh.class);

    private final OfficialAllCompaniesPreparationOperation operation;
    private final String mode;
    private final String apiKey;
    private final int businessYear;
    private final int batchSize;
    private final long pauseMillis;
    private final Duration pauseAfterStop;
    private final Executor executor;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Set<String> skipped = ConcurrentHashMap.newKeySet();
    private volatile Instant pausedUntil = Instant.EPOCH;

    @Autowired
    public ScheduledAllCompaniesRefresh(OfficialAllCompaniesPreparationOperation operation,
            @Value("${aira.demo.bootstrap.mode:}") String mode,
            @Value("${aira.opendart.api-key:}") String apiKey,
            @Value("${aira.demo.bootstrap.business-year:2025}") int businessYear,
            @Value("${aira.opendart.all-companies.batch-size:100}") int batchSize,
            @Value("${aira.opendart.all-companies.pause-ms:300}") long pauseMillis,
            @Value("${aira.opendart.all-companies.pause-hours:6}") long pauseHours) {
        this(operation, mode, apiKey, businessYear, batchSize, pauseMillis, Duration.ofHours(pauseHours),
                Executors.newSingleThreadExecutor(runnable -> {
                    var thread = new Thread(runnable, "opendart-all-companies");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    ScheduledAllCompaniesRefresh(OfficialAllCompaniesPreparationOperation operation, String mode, String apiKey,
            int businessYear, int batchSize, long pauseMillis, Duration pauseAfterStop, Executor executor) {
        this.operation = operation;
        this.mode = mode;
        this.apiKey = apiKey;
        this.businessYear = businessYear;
        this.batchSize = batchSize;
        this.pauseMillis = pauseMillis;
        this.pauseAfterStop = pauseAfterStop;
        this.executor = executor;
    }

    @Scheduled(initialDelayString = "${aira.opendart.all-companies.initial-delay-ms:180000}",
            fixedDelayString = "${aira.opendart.all-companies.delay-ms:300000}")
    public void tick() {
        if (!"live".equals(mode)) {
            log.warn("OpenDART all-company refresh skipped: aira.demo.bootstrap.mode is not 'live'");
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OpenDART all-company refresh skipped: OPENDART_API_KEY is not configured");
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
            var result = operation.run(businessYear, batchSize, pauseMillis, skipped);
            log.info(result.summary());
            if (result.attempted() == 0) {
                pausedUntil = Instant.now().plus(pauseAfterStop);
                log.info("OpenDART all-company refresh has nothing pending; next check after {}", pauseAfterStop);
            } else if (result.stopReason() != OfficialAllCompaniesPreparationOperation.StopReason.NONE) {
                pausedUntil = Instant.now().plus(pauseAfterStop);
                log.warn("OpenDART all-company refresh paused until {} ({})", pausedUntil, result.stopReason());
            }
        } catch (RuntimeException e) {
            pausedUntil = Instant.now().plus(Duration.ofMinutes(30));
            log.error("OpenDART all-company refresh failed; retrying after 30 minutes", e);
        } finally {
            running.set(false);
        }
    }
}
