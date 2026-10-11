package com.aira.api.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aira.demo.bootstrap.enabled", havingValue = "true")
public class OfficialDemoBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(OfficialDemoBootstrapRunner.class);

    private final OfficialDemoBootstrapOperation bootstrap;
    private final String mode;
    private final String apiKey;
    private final int businessYear;

    public OfficialDemoBootstrapRunner(OfficialDemoBootstrapOperation bootstrap,
            @Value("${aira.demo.bootstrap.mode:}") String mode,
            @Value("${aira.opendart.api-key:}") String apiKey,
            @Value("${aira.demo.bootstrap.business-year:2025}") int businessYear) {
        this.bootstrap = bootstrap;
        this.mode = mode;
        this.apiKey = apiKey;
        this.businessYear = businessYear;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"live".equals(mode)) {
            throw new IllegalStateException(
                    "Official demo bootstrap requires aira.demo.bootstrap.mode=live");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Official demo bootstrap live mode requires OPENDART_API_KEY");
        }
        // 외부(OpenDART) 응답이 일시적으로 이상해도 서버 전체가 꺼지지 않게 한다.
        // 설정 오류(모드/키 없음)는 위에서 그대로 시작을 막고, 데이터 준비 실패만 기록하고 계속 켠다.
        try {
            log.info(bootstrap.prepare(businessYear).summary());
        } catch (RuntimeException ex) {
            log.error("Official demo bootstrap failed; the server keeps running with the data already stored: {}",
                    ex.toString(), ex);
        }
    }
}
