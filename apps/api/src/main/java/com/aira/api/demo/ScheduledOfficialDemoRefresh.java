package com.aira.api.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루에 한 번 OpenDART 공식 공시(대표 기업)를 다시 확인해, 새로 올라온 공시와 그에 연결된 사건·판단을 반영한다.
 * 기존 OfficialDemoBootstrapOperation을 그대로 다시 부를 뿐이다. 하루 한 번만 부르는 이유는 OpenDART 호출 한도를
 * 아끼기 위해서이며, 시각은 aira.refresh.scheduler.opendart-cron으로 바꿀 수 있다.
 */
@Component
@ConditionalOnProperty(name = "aira.refresh.scheduler.enabled", havingValue = "true")
public class ScheduledOfficialDemoRefresh {
    private static final Logger log = LoggerFactory.getLogger(ScheduledOfficialDemoRefresh.class);

    private final OfficialDemoBootstrapOperation bootstrap;
    private final String mode;
    private final String apiKey;
    private final int businessYear;

    public ScheduledOfficialDemoRefresh(OfficialDemoBootstrapOperation bootstrap,
            @Value("${aira.demo.bootstrap.mode:}") String mode,
            @Value("${aira.opendart.api-key:}") String apiKey,
            @Value("${aira.demo.bootstrap.business-year:2025}") int businessYear) {
        this.bootstrap = bootstrap;
        this.mode = mode;
        this.apiKey = apiKey;
        this.businessYear = businessYear;
    }

    @Scheduled(cron = "${aira.refresh.scheduler.opendart-cron:0 0 9 * * *}", zone = "Asia/Seoul")
    public void refresh() {
        if (!"live".equals(mode)) {
            log.warn("Scheduled OpenDART refresh skipped: aira.demo.bootstrap.mode is not 'live'");
            return;
        }
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Scheduled OpenDART refresh skipped: OPENDART_API_KEY is not configured");
            return;
        }
        try {
            log.info("Scheduled {}", bootstrap.prepare(businessYear).summary());
        } catch (RuntimeException e) {
            log.error("Scheduled OpenDART refresh failed", e);
        }
    }
}
