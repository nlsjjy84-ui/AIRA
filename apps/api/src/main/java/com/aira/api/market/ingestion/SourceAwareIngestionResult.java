package com.aira.api.market.ingestion;

import com.aira.api.market.domain.Evidence;
import com.aira.api.market.domain.Event;
import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.Source;

public record SourceAwareIngestionResult(
        Source source,
        Evidence evidence,
        Event event,
        Fact fact) {}
