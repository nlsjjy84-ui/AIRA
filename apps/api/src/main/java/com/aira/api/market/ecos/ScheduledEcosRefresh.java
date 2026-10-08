package com.aira.api.market.ecos;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 한국은행 ECOS 실질 GDP(분기)를 하루에 한 번 다시 가져온다.
 *
 * <p>수집 코드(EcosRealGdpIngestionOperation)는 이미 있었지만 서버 시작이나 스케줄에서 아무도 부르지 않아
 * GDP 화면이 비어 있었다. 이 클래스는 그 Operation을 최근 N개 분기 범위로 부를 뿐이다. 같은 분기를 다시
 * 가져와도 결과가 중복되지 않도록 Operation이 이미 만들어져 있다(재실행 시 같은 결과).
 *
 * <p>aira.refresh.scheduler.enabled=true 일 때만 켜지고, 인증키(BOK_ECOS_API_KEY)가 없으면 건너뛴다.
 */
@Component
@ConditionalOnProperty(name = "aira.refresh.scheduler.enabled", havingValue = "true")
public class ScheduledEcosRefresh {
    private static final Logger log = LoggerFactory.getLogger(ScheduledEcosRefresh.class);
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

    private final EcosRealGdpIngestionOperation operation;
    private final String apiKey;
    private final int quarters;
    private final Clock clock;

    @Autowired
    public ScheduledEcosRefresh(EcosRealGdpIngestionOperation operation,
            @Value("${aira.ecos.api-key:}") String apiKey,
            @Value("${aira.refresh.scheduler.ecos-quarters:8}") int quarters) {
        this(operation, apiKey, quarters, Clock.system(SEOUL));
    }

    ScheduledEcosRefresh(EcosRealGdpIngestionOperation operation, String apiKey, int quarters, Clock clock) {
        this.operation = operation;
        this.apiKey = apiKey;
        this.quarters = Math.max(1, Math.min(quarters, 40));
        this.clock = clock;
    }

    @Scheduled(cron = "${aira.refresh.scheduler.ecos-cron:0 0 10 * * *}", zone = "Asia/Seoul")
    public void refresh() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Scheduled ECOS GDP refresh skipped: BOK_ECOS_API_KEY is not configured");
            return;
        }
        try {
            var scope = scope();
            var result = operation.ingest(scope, OffsetDateTime.now(clock));
            log.info("Scheduled ECOS real GDP refresh {}..{} facts={}",
                    scope.startTime(), scope.endTime(), result.factIds().size());
        } catch (RuntimeException e) {
            log.error("Scheduled ECOS real GDP refresh failed", e);
        }
    }

    EcosRealGdpObservationScope scope() {
        YearMonth now = YearMonth.now(clock);
        int endIndex = now.getYear() * 4 + (now.getMonthValue() - 1) / 3;
        int startIndex = endIndex - (quarters - 1);
        return new EcosRealGdpObservationScope(label(startIndex), label(endIndex),
                new EcosObservationBudget(quarters, 1, 3));
    }

    private static String label(int index) {
        return (index / 4) + "Q" + (index % 4 + 1);
    }
}
