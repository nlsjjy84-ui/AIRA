package com.aira.api.market.ingestion;

public interface EarningsIngestionBoundary {
    IngestionReceipt ingest(SourceAwareEarningsIngestionInput input);
}
