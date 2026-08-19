package com.aira.api.market.ingestion;

import org.springframework.stereotype.Service;

@Service
public class SourceAwareEarningsIngestionBoundary implements EarningsIngestionBoundary {
    private final SourceAwareEarningsIngestionService ingestion;

    public SourceAwareEarningsIngestionBoundary(SourceAwareEarningsIngestionService ingestion) {
        this.ingestion = ingestion;
    }

    @Override
    public IngestionReceipt ingest(SourceAwareEarningsIngestionInput input) {
        return IngestionReceipt.from(ingestion.ingest(input));
    }
}
