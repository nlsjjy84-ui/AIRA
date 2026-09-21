package com.aira.api.market.krx;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KrxLatestIndexTradingDayResolver {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int DISCOVERY_DATES = 14;
    private final KrxClient client;
    private final Clock clock;

    @Autowired
    public KrxLatestIndexTradingDayResolver(KrxClient client) { this(client, Clock.systemUTC()); }
    KrxLatestIndexTradingDayResolver(KrxClient client, Clock clock) { this.client = client; this.clock = clock; }

    public KrxSnapshot resolve(KrxDataset dataset) {
        if (dataset != KrxDataset.KOSPI_INDEX && dataset != KrxDataset.KOSDAQ_INDEX)
            throw new IllegalArgumentException("Approved KRX index dataset is required");
        LocalDate candidate = LocalDate.now(clock.withZone(KST));
        for (int offset = 0; offset < DISCOVERY_DATES; offset++) {
            LocalDate date = candidate.minusDays(offset);
            KrxSnapshot snapshot = client.fetch(dataset, date);
            if (snapshot.dataset() != dataset || !snapshot.date().equals(date))
                throw new IllegalStateException("KRX index snapshot identity mismatch");
            if (snapshot.rows().isEmpty()) continue;
            KrxIndexPacket.from(snapshot);
            return snapshot;
        }
        throw new IllegalStateException("No official KRX index snapshot in discovery budget");
    }
}
