package com.aira.api.market.opendart;

import static com.aira.api.market.opendart.OpenDartPeriodWitnessResolver.blocked;
import static com.aira.api.market.opendart.OpenDartProviderException.Category.*;

import com.aira.api.market.domain.*;
import com.aira.api.market.ingestion.*;
import com.aira.api.market.repository.*;
import com.aira.api.market.service.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OpenDartFilingPersistence {
    private final JdbcTemplate jdbc;
    private final EntityExternalIdentifierRegistryService identifiers;
    private final CompanyEntityBootstrapOperation companies;
    private final EarningsIngestionBoundary boundary;
    private final SourceRepository sources;
    private final EvidenceRegistrationStore evidenceRegistrations;
    private final EvidenceRepository evidence;
    private final FactRepository facts;
    private final FactPeriodEvidenceRegistrationStore periodLinks;

    public OpenDartFilingPersistence(JdbcTemplate jdbc, EntityExternalIdentifierRegistryService identifiers,
            CompanyEntityBootstrapOperation companies, EarningsIngestionBoundary boundary,
            SourceRepository sources, EvidenceRegistrationStore evidenceRegistrations,
            EvidenceRepository evidence, FactRepository facts, FactPeriodEvidenceRegistrationStore periodLinks) {
        this.jdbc = jdbc;
        this.identifiers = identifiers;
        this.companies = companies;
        this.boundary = boundary;
        this.sources = sources;
        this.evidenceRegistrations = evidenceRegistrations;
        this.evidence = evidence;
        this.facts = facts;
        this.periodLinks = periodLinks;
    }

    @Transactional(readOnly = true)
    public void preflight(OpenDartPreparedFiling filing) {
        var company = identifiers.findEntity(key(filing)).orElse(null);
        checkLegacy(filing, company);
    }

    private void checkLegacy(OpenDartPreparedFiling filing, MarketEntity company) {
        if (company != null && company.getEntityType() != EntityType.COMPANY) {
            throw blocked(PERIOD_WITNESS_IDENTITY_MISMATCH);
        }
        for (var metric : filing.metrics()) {
            var rows = jdbc.query("""
                    SELECT f.subject_entity_id,f.period_start,f.period_end
                    FROM fact f JOIN fact_assertion fa ON fa.fact_id=f.id
                    JOIN evidence e ON e.id=fa.evidence_id JOIN source s ON s.id=e.source_id
                    WHERE s.source_type='REGULATOR' AND s.external_key='opendart'
                      AND e.external_id=? AND f.predicate=?
                    """, (rs, row) -> new Legacy(rs.getObject(1, UUID.class),
                    rs.getObject(2, LocalDate.class), rs.getObject(3, LocalDate.class)),
                    filing.valueEvidence().externalId(), metric.predicate().name());
            for (var existing : rows) {
                if (company == null || !company.getId().equals(existing.company())) {
                    throw blocked(PERIOD_WITNESS_IDENTITY_MISMATCH);
                }
                if (!filing.period().start().equals(existing.start()) || !filing.period().end().equals(existing.end())) {
                    throw blocked(LEGACY_PERIOD_MISMATCH);
                }
            }
        }
    }

    // Recheck after locking: another filing may have committed between network validation and persistence.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Saved persist(OpenDartPreparedFiling filing, OpenDartCompanyDirectoryRecord bootstrap) {
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",
                Object.class, "OPENDART:COMPANY:" + filing.context().corpCode());
        var company = identifiers.findEntity(key(filing)).orElse(null);
        checkLegacy(filing, company);
        if (company == null) {
            if (bootstrap == null || !bootstrap.corpCode().equals(filing.context().corpCode())) {
                throw blocked(PERIOD_WITNESS_IDENTITY_MISMATCH);
            }
            var created = companies.create(new CompanyEntityBootstrapCommand(bootstrap.corpName(), "KR"));
            identifiers.registerOrReuse(new ExternalIdentifierRegistration(created.entityId(), key(filing)));
            company = identifiers.findEntity(key(filing)).orElseThrow();
        }
        var receipts = new ArrayList<IngestionReceipt>();
        for (var input : filing.inputs(company.getId(), company.getCanonicalName())) {
            receipts.add(boundary.ingest(input));
        }
        var source = sources.findById(receipts.getFirst().sourceId()).orElseThrow();
        UUID witnessId = evidenceRegistrations.registerOrGetId(source, filing.period().evidence());
        var witness = evidence.findById(witnessId).orElseThrow();
        for (var receipt : receipts) {
            periodLinks.registerOrReuse(FactPeriodEvidence.verified(facts.findById(receipt.factId()).orElseThrow(),
                    witness, filing.period().locator(), filing.period().start(), filing.period().end(),
                    filing.period().evidence().collectedAt()));
        }
        return new Saved(company.getId(), List.copyOf(receipts));
    }

    private static ExternalIdentifierKey key(OpenDartPreparedFiling filing) {
        return new ExternalIdentifierKey("OPENDART", "CORP_CODE", filing.context().corpCode());
    }

    private record Legacy(UUID company, LocalDate start, LocalDate end) {}
    public record Saved(UUID companyId, List<IngestionReceipt> receipts) {}
}
