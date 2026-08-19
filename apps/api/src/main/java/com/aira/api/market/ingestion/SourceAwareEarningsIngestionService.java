package com.aira.api.market.ingestion;

import com.aira.api.market.domain.Evidence;
import com.aira.api.market.domain.Event;
import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.Source;
import com.aira.api.market.normalization.EarningsFactNormalizationInput;
import com.aira.api.market.normalization.EarningsFactNormalizationService;
import com.aira.api.market.normalization.EarningsNormalizationInput;
import com.aira.api.market.normalization.EarningsEventNormalizationService;
import com.aira.api.market.repository.EvidenceRepository;
import com.aira.api.market.repository.EvidenceRegistrationStore;
import com.aira.api.market.service.SourceRegistryService;
import java.util.Arrays;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceAwareEarningsIngestionService {
    private final SourceRegistryService sources;
    private final EvidenceRegistrationStore evidenceRegistrations;
    private final EvidenceRepository evidence;
    private final EarningsEventNormalizationService events;
    private final EarningsFactNormalizationService facts;

    public SourceAwareEarningsIngestionService(SourceRegistryService sources,
            EvidenceRegistrationStore evidenceRegistrations, EvidenceRepository evidence,
            EarningsEventNormalizationService events,
            EarningsFactNormalizationService facts) {
        this.sources = sources;
        this.evidenceRegistrations = evidenceRegistrations;
        this.evidence = evidence;
        this.events = events;
        this.facts = facts;
    }

    @Transactional
    public SourceAwareIngestionResult ingest(SourceAwareEarningsIngestionInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Source-aware ingestion input is required");
        }
        Source source = sources.registerOrReuse(input.source());
        EvidenceRegistration evidenceInput = input.evidence();
        var evidenceId = evidenceRegistrations.registerOrGetId(source, evidenceInput);
        Evidence savedEvidence = evidence.findById(evidenceId)
                .orElseThrow(() -> new IllegalStateException("Registered evidence was not found"));
        if (!Arrays.equals(savedEvidence.getContentHash(), evidenceInput.contentHash())) {
            throw new IllegalStateException(
                    "Evidence identity conflicts with different filing content");
        }

        Event event = events.normalize(new EarningsNormalizationInput(input.subjectEntityId(),
                savedEvidence.getId(), input.reportingPeriodEnd(), input.neutralTitle(),
                input.occurredAt()));
        Fact fact = facts.normalize(new EarningsFactNormalizationInput(event.getId(),
                input.subjectEntityId(), savedEvidence.getId(), input.predicate(),
                input.numberValue(), input.currencyCode(), input.periodStart(),
                input.periodEnd(), input.assertionLocator()));
        return new SourceAwareIngestionResult(source, savedEvidence, event, fact);
    }
}
