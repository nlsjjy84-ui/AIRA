package com.aira.api.market.repository;

import com.aira.api.market.domain.MarketEntity;
import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketEntityRepository extends JpaRepository<MarketEntity, UUID> {
    java.util.Optional<MarketEntity> findByCanonicalKey(String canonicalKey);

    @Query("""
            select distinct entity
            from FactAssertion assertion
            join assertion.fact fact
            join fact.subjectEntity entity
            join assertion.evidence evidence
            join evidence.source source
            where entity.entityType = com.aira.api.market.domain.EntityType.COMPANY
              and entity.active = true
              and fact.status = com.aira.api.market.domain.FactStatus.SUPPORTED
              and fact.valueNumber is not null
              and fact.periodStart is not null
              and fact.periodEnd is not null
              and fact.predicate in (com.aira.api.market.domain.FactPredicate.REVENUE,
                                     com.aira.api.market.domain.FactPredicate.OPERATING_INCOME,
                                     com.aira.api.market.domain.FactPredicate.NET_INCOME,
                                     com.aira.api.market.domain.FactPredicate.TOTAL_ASSETS,
                                     com.aira.api.market.domain.FactPredicate.TOTAL_LIABILITIES,
                                     com.aira.api.market.domain.FactPredicate.TOTAL_EQUITY)
              and evidence.externalId is not null
              and evidence.originalUrl is not null
              and evidence.collectedAt is not null
              and source.name is not null
            order by entity.canonicalName, entity.id
            """)
    List<MarketEntity> findPubliclyAvailableCompanies();
}
