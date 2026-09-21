package com.aira.api.market.krx;

import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class KrxIndexRefreshOperation {
    private final KrxLatestIndexTradingDayResolver resolver;
    private final KrxPersistence persistence;

    public KrxIndexRefreshOperation(KrxLatestIndexTradingDayResolver resolver, KrxPersistence persistence) {
        this.resolver = resolver;
        this.persistence = persistence;
    }

    public Result refreshLatest() {
        KrxSnapshot kospi = resolver.resolve(KrxDataset.KOSPI_INDEX);
        KrxSnapshot kosdaq = resolver.resolve(KrxDataset.KOSDAQ_INDEX);
        persistence.index(kospi);
        persistence.index(kosdaq);
        return new Result(kospi.date(), kosdaq.date());
    }

    public record Result(LocalDate kospiDate, LocalDate kosdaqDate) {
        public String summary() {
            return "KRX index refresh complete: KOSPI=" + kospiDate + ", KOSDAQ=" + kosdaqDate;
        }
    }
}
