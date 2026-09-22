package com.aira.api.market.ecos;

import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EcosRealGdpIngestionOperation {
    private final EcosRealGdpObservationReader reader;
    private final EcosRealGdpFactIngestionService persistence;

    public EcosRealGdpIngestionOperation(EcosRealGdpObservationReader reader,
            EcosRealGdpFactIngestionService persistence) {
        this.reader = Objects.requireNonNull(reader);
        this.persistence = Objects.requireNonNull(persistence);
    }

    // Suspend any caller transaction: provider HTTP must finish before database work.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public EcosRealGdpFactIngestionResult ingest(EcosRealGdpObservationScope scope,
            OffsetDateTime collectedAt) {
        if (scope == null || collectedAt == null)
            throw new IllegalArgumentException("ECOS scope and collection time are required");
        var result = reader.read(scope);
        // Existing service atomically binds series, registers Evidence, and ingests Facts.
        return persistence.ingest(result, collectedAt);
    }
}
