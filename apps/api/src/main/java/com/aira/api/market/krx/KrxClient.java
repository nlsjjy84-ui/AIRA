package com.aira.api.market.krx;

import java.time.LocalDate;

public interface KrxClient {
    KrxSnapshot fetch(KrxDataset dataset, LocalDate date);
}
