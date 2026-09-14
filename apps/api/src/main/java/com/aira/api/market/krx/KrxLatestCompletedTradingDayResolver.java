package com.aira.api.market.krx;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class KrxLatestCompletedTradingDayResolver {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DISCOVERY_DATES = 14;
    private final KrxClient client;
    private final Clock clock;
    @Autowired
    public KrxLatestCompletedTradingDayResolver(KrxClient client) { this(client, Clock.systemUTC()); }
    KrxLatestCompletedTradingDayResolver(KrxClient client, Clock clock) { this.client = client; this.clock = clock; }

    public KrxSnapshot resolve(String market) {
        KrxDataset dataset = switch (market) {
            case "KOSPI" -> KrxDataset.STK_DAILY;
            case "KOSDAQ" -> KrxDataset.KSQ_DAILY;
            default -> throw new IllegalArgumentException("Only approved KRX stock markets are supported");
        };
        LocalDate candidate = LocalDate.now(clock.withZone(KST));
        for (int offset = 0; offset < DISCOVERY_DATES; offset++) {
            LocalDate date = candidate.minusDays(offset);
            // A daily response is official calendar evidence; no weekday/holiday table is inferred.
            KrxSnapshot snapshot = client.fetch(dataset, date);
            if (snapshot.dataset() != dataset || !snapshot.date().equals(date))
                throw new IllegalStateException("KRX daily snapshot identity mismatch");
            validateDaily(snapshot);
            if (!snapshot.rows().isEmpty()) return snapshot;
        }
        throw new IllegalStateException("No completed official KRX stock snapshot in discovery budget");
    }

    static void validateDaily(KrxSnapshot snapshot) {
        for (var row : snapshot.rows()) {
            if (row.get("ISU_CD") == null || row.get("ISU_CD").isBlank())
                throw new IllegalArgumentException("KRX daily security identity is missing");
            for (var predicate : java.util.Map.of("TDD_CLSPRC", com.aira.api.market.domain.FactPredicate.CLOSE_PRICE,
                    "TDD_OPNPRC", com.aira.api.market.domain.FactPredicate.OPEN_PRICE,
                    "TDD_HGPRC", com.aira.api.market.domain.FactPredicate.HIGH_PRICE,
                    "TDD_LWPRC", com.aira.api.market.domain.FactPredicate.LOW_PRICE,
                    "ACC_TRDVOL", com.aira.api.market.domain.FactPredicate.TRADING_VOLUME,
                    "ACC_TRDVAL", com.aira.api.market.domain.FactPredicate.TRADING_VALUE,
                    "MKTCAP", com.aira.api.market.domain.FactPredicate.MARKET_CAP,
                    "LIST_SHRS", com.aira.api.market.domain.FactPredicate.LISTED_SHARES).entrySet())
                KrxPreparedPacket.parseNumber(row.get(predicate.getKey()), predicate.getValue());
            String change = row.get("FLUC_RT");
            if (change != null && !change.isBlank() && !change.trim().equals("-")
                    && !change.trim().matches("[+-]?[0-9]+(?:\\.[0-9]+)?"))
                throw new IllegalArgumentException("Malformed KRX daily change rate");
        }
    }
}
