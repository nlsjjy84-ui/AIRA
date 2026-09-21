package com.aira.api.market.krx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aira.krx.index-refresh.enabled", havingValue = "true")
public class KrxIndexRefreshRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(KrxIndexRefreshRunner.class);
    private final KrxIndexRefreshOperation operation;
    private final String mode;
    private final String authKey;

    public KrxIndexRefreshRunner(KrxIndexRefreshOperation operation,
            @Value("${aira.krx.index-refresh.mode:}") String mode,
            @Value("${aira.krx.auth-key:}") String authKey) {
        this.operation = operation;
        this.mode = mode;
        this.authKey = authKey;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"live".equals(mode))
            throw new IllegalStateException("KRX index refresh requires aira.krx.index-refresh.mode=live");
        if (authKey == null || authKey.isBlank())
            throw new IllegalStateException("KRX index refresh live mode requires AIRA_KRX_AUTH_KEY");
        log.info(operation.refreshLatest().summary());
    }
}
