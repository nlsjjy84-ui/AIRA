package com.aira.api.market.refresh;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** aira.refresh.scheduler.enabled=true 일 때만 예약 실행(@Scheduled)을 켠다. 꺼져 있으면 기존 동작과 완전히 같다. */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "aira.refresh.scheduler.enabled", havingValue = "true")
public class RefreshSchedulingConfig {
}
