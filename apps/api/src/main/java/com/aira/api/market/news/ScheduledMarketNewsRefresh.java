package com.aira.api.market.news;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 시장 뉴스를 화면이 열릴 때가 아니라 미리(약 8분마다) 받아 저장해 둔다.
 *
 * <p>GDELT는 응답이 느리고 요청이 잦으면 429로 거절한다. 방문자가 직접 기다리지 않도록 서버가 미리
 * 받아 두고, 화면은 저장된 결과를 바로 보여준다. 캐시 유효시간(기본 10분)보다 짧은 간격으로 돈다.
 *
 * <p>aira.refresh.scheduler.enabled=true 일 때만 켜진다.
 */
@Component
@ConditionalOnProperty(name = "aira.refresh.scheduler.enabled", havingValue = "true")
public class ScheduledMarketNewsRefresh {
    private static final Logger log = LoggerFactory.getLogger(ScheduledMarketNewsRefresh.class);
    private final RecentMarketNewsService service;

    @Autowired
    public ScheduledMarketNewsRefresh(RecentMarketNewsService service) { this.service = service; }

    @Scheduled(initialDelayString = "${aira.refresh.scheduler.news-initial-delay-ms:20000}",
            fixedDelayString = "${aira.refresh.scheduler.news-delay-ms:480000}")
    public void refresh() {
        try {
            service.refresh();
        } catch (RuntimeException e) {
            log.warn("Scheduled market news refresh failed", e);
        }
    }
}
