package com.aira.api.market.ecos;

import com.aira.api.market.domain.Source;
import com.aira.api.market.repository.EvidenceRegistrationStore;
import com.aira.api.market.service.SourceRegistryService;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EcosRealGdpEvidenceRegistrationService {
    private final SourceRegistryService sources;
    private final EvidenceRegistrationStore evidences;

    public EcosRealGdpEvidenceRegistrationService(
            SourceRegistryService sources,
            EvidenceRegistrationStore evidences) {
        if (sources == null || evidences == null) {
            throw new IllegalArgumentException("ECOS evidence registration dependencies are required");
        }
        this.sources = sources;
        this.evidences = evidences;
    }
    @Transactional
    public UUID register(
            EcosRealGdpObservationResult result,
            OffsetDateTime collectedAt) {
        var registration = EcosRealGdpEvidenceSnapshotFactory.create(result, collectedAt);
        Source source = sources.registerOrReuse(EcosOfficialSourceContract.BOK_ECOS);
        EcosOfficialSourceContract.requireCanonicalBokEcos(source);
        return evidences.registerOrGetId(source, registration);
    }
}
