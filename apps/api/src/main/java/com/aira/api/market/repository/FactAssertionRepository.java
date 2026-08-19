package com.aira.api.market.repository;

import com.aira.api.market.domain.FactAssertion;
import com.aira.api.market.domain.FactAssertionId;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FactAssertionRepository
        extends JpaRepository<FactAssertion, FactAssertionId> {
    @Query("""
            select assertion from FactAssertion assertion
            join fetch assertion.fact fact
            join fetch assertion.evidence evidence
            join fetch evidence.source source
            where fact.id in :factIds
            """)
    List<FactAssertion> findWithProvenanceByFactIds(
            @Param("factIds") Collection<UUID> factIds);
}
