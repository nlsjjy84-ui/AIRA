package com.aira.api.market.krx;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "aira.krx.stock-refresh.enabled", havingValue = "true")
public class KrxStockRefreshRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(KrxStockRefreshRunner.class);

    private final KrxStockRefreshOperation operation;
    private final String mode;
    private final String authKey;
    private final String targets;

    public KrxStockRefreshRunner(KrxStockRefreshOperation operation,
            @Value("${aira.krx.stock-refresh.mode:}") String mode,
            @Value("${aira.krx.auth-key:}") String authKey,
            @Value("${aira.krx.stock-refresh.targets:KOSPI:000660,KOSPI:035420}") String targets) {
        this.operation = operation;
        this.mode = mode;
        this.authKey = authKey;
        this.targets = targets;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"live".equals(mode)) {
            throw new IllegalStateException("KRX stock refresh requires aira.krx.stock-refresh.mode=live");
        }
        if (authKey == null || authKey.isBlank()) {
            throw new IllegalStateException("KRX stock refresh live mode requires AIRA_KRX_AUTH_KEY");
        }
        log.info(operation.refreshLatest(parseTargets(targets)).summary());
    }

    static List<KrxStockRefreshOperation.Target> parseTargets(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("KRX stock refresh targets are required");
        }
        var dedup = new LinkedHashSet<KrxStockRefreshOperation.Target>();
        for (String token : raw.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("KRX stock refresh target must use MARKET:SHORT_CODE");
            }
            dedup.add(new KrxStockRefreshOperation.Target(parts[0], parts[1]));
        }
        if (dedup.isEmpty()) {
            throw new IllegalArgumentException("KRX stock refresh targets are required");
        }
        return new ArrayList<>(dedup);
    }
}
