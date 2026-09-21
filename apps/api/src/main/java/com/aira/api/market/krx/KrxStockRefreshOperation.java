package com.aira.api.market.krx;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class KrxStockRefreshOperation {
    private final KrxLatestCompletedTradingDayResolver resolver;
    private final KrxClient client;
    private final KrxPersistence persistence;

    public KrxStockRefreshOperation(KrxLatestCompletedTradingDayResolver resolver,
            KrxClient client, KrxPersistence persistence) {
        this.resolver = resolver;
        this.client = client;
        this.persistence = persistence;
    }

    public Result refreshLatest(List<Target> targets) {
        if (targets == null || targets.isEmpty()) {
            throw new IllegalArgumentException("At least one KRX stock refresh target is required");
        }

        Map<String, LinkedHashSet<String>> byMarket = new LinkedHashMap<>();
        for (Target target : targets) {
            if (target == null) throw new IllegalArgumentException("KRX stock refresh target is required");
            byMarket.computeIfAbsent(target.market(), ignored -> new LinkedHashSet<>())
                    .add(target.shortCode());
        }

        List<MarketResult> results = new ArrayList<>();
        for (var entry : byMarket.entrySet()) {
            String market = entry.getKey();
            KrxSnapshot daily = resolver.resolve(market);
            KrxDataset baseDataset = switch (market) {
                case "KOSPI" -> KrxDataset.STK_BASE;
                case "KOSDAQ" -> KrxDataset.KSQ_BASE;
                default -> throw new IllegalArgumentException("Unsupported KRX stock market");
            };
            KrxSnapshot base = client.fetch(baseDataset, daily.date());
            KrxPreparedPacket packet = KrxPreparedPacket.stock(base, daily)
                    .selectShortCodes(Set.copyOf(entry.getValue()));
            persistence.stock(packet);
            results.add(new MarketResult(market, daily.date(), packet.securities().size(),
                    packet.values().size()));
        }

        return new Result(List.copyOf(results));
    }

    public record Target(String market, String shortCode) {
        public Target {
            if (market == null || shortCode == null) {
                throw new IllegalArgumentException("KRX target market and short code are required");
            }
            market = market.trim().toUpperCase(Locale.ROOT);
            shortCode = shortCode.trim();
            if (!Set.of("KOSPI", "KOSDAQ").contains(market)) {
                throw new IllegalArgumentException("KRX target market must be KOSPI or KOSDAQ");
            }
            if (!shortCode.matches("[0-9]{6}")) {
                throw new IllegalArgumentException("KRX target short code must be exactly six digits");
            }
        }
    }

    public record MarketResult(String market, LocalDate tradingDate,
            int securityCount, int factValueCount) {}

    public record Result(List<MarketResult> markets) {
        public Result {
            markets = List.copyOf(markets);
        }

        public String summary() {
            return markets.stream()
                    .map(result -> result.market() + "=" + result.tradingDate()
                            + " securities=" + result.securityCount()
                            + " values=" + result.factValueCount())
                    .collect(java.util.stream.Collectors.joining(", ",
                            "KRX stock refresh complete: ", ""));
        }
    }
}
