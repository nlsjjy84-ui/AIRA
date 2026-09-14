package com.aira.api.market.repository;

import com.aira.api.market.domain.FactPeriodEvidence;
import com.aira.api.market.domain.FactPeriodEvidenceId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.Repository;

public interface FactPeriodEvidenceRepository
        extends Repository<FactPeriodEvidence, FactPeriodEvidenceId> {
    @EntityGraph(attributePaths = {"evidence", "evidence.source"})
    List<FactPeriodEvidence> findByFact_IdOrderByCreatedAtAscEvidence_IdAsc(UUID factId);
}
