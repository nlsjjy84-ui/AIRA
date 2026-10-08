package com.aira.api.market.krx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 하루에 정해진 시각마다 KRX 공식 일별 데이터(지수·종목)를 다시 가져온다.
 *
 * <p>지금까지는 서버가 켜질 때 한 번(ApplicationRunner)만 가져와서, 서버를 다시 켜지 않으면 화면이
 * 며칠 전 값에서 멈춰 있었다. 이 클래스는 같은 Operation을 정해진 시각에 다시 부를 뿐이며 새로운 수집
 * 방식을 만들지 않는다. 장중 실시간 시세가 아니라 "최근 완료된 공식 거래일" 값을 최신으로 유지하는 용도다.
 *
 * <p>aira.refresh.scheduler.enabled=true 일 때만 켜지고, 기존 Runner와 같은 조건(mode=live + 인증키)을
 * 만족하지 않으면 경고만 남기고 건너뛴다. 한 작업이 실패해도 다른 작업은 계속 시도한다.
 */
@Component
@ConditionalOnProperty(name = "aira.refresh.scheduler.enabled", havingValue = "true")
public class ScheduledKrxRefresh {
    private static final Logger log = LoggerFactory.getLogger(ScheduledKrxRefresh.class);

    private final KrxIndexRefreshOperation indexRefresh;
    private final KrxStockRefreshOperation stockRefresh;
    private final String authKey;
    private final String indexMode;
    private final String stockMode;
    private final String stockTargets;

    public ScheduledKrxRefresh(KrxIndexRefreshOperation indexRefresh, KrxStockRefreshOperation stockRefresh,
            @Value("${aira.krx.auth-key:}") String authKey,
            @Value("${aira.krx.index-refresh.mode:}") String indexMode,
            @Value("${aira.krx.stock-refresh.mode:}") String stockMode,
            @Value("${aira.krx.stock-refresh.targets:KOSPI:000660,KOSPI:035420}") String stockTargets) {
        this.indexRefresh = indexRefresh;
        this.stockRefresh = stockRefresh;
        this.authKey = authKey;
        this.indexMode = indexMode;
        this.stockMode = stockMode;
        this.stockTargets = stockTargets;
    }

    @Scheduled(cron = "${aira.refresh.scheduler.cron:0 30 8,18 * * *}", zone = "Asia/Seoul")
    public void refresh() {
        if (authKey == null || authKey.isBlank()) {
            log.warn("Scheduled KRX refresh skipped: AIRA_KRX_AUTH_KEY is not configured");
            return;
        }
        if ("live".equals(indexMode)) {
            try {
                log.info("Scheduled {}", indexRefresh.refreshLatest().summary());
            } catch (RuntimeException e) {
                log.error("Scheduled KRX index refresh failed", e);
            }
        } else {
            log.warn("Scheduled KRX index refresh skipped: aira.krx.index-refresh.mode is not 'live'");
        }
        if ("live".equals(stockMode)) {
            try {
                log.info("Scheduled {}", stockRefresh.refreshLatest(KrxStockRefreshRunner.parseTargets(stockTargets)).summary());
            } catch (RuntimeException e) {
                log.error("Scheduled KRX stock refresh failed", e);
            }
        } else {
            log.warn("Scheduled KRX stock refresh skipped: aira.krx.stock-refresh.mode is not 'live'");
        }
    }
}
