package com.aira.api.market.krx;

import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class KrxIngestion {
    private final KrxClient client;
    private final KrxPersistence persistence;
    public KrxIngestion(KrxClient client, KrxPersistence persistence) {
        this.client = client;
        this.persistence = persistence;
    }
    public void stock(String market, LocalDate date) {
        KrxDataset base = switch (market) {
            case "KOSPI" -> KrxDataset.STK_BASE;
            case "KOSDAQ" -> KrxDataset.KSQ_BASE;
            default -> throw new IllegalArgumentException("Unsupported KRX market");
        };
        KrxDataset daily = market.equals("KOSPI") ? KrxDataset.STK_DAILY : KrxDataset.KSQ_DAILY;
        // Both HTTP responses and every code/value decision are finalized before the atomic DB bundle.
        var packet = KrxPreparedPacket.stock(client.fetch(base, date), client.fetch(daily, date));
        persistence.stock(packet);
    }
    public void index(KrxDataset dataset, LocalDate date) {
        if (dataset != KrxDataset.KOSPI_INDEX && dataset != KrxDataset.KOSDAQ_INDEX)
            throw new IllegalArgumentException("Index dataset is required");
        persistence.index(client.fetch(dataset, date));
    }
}
