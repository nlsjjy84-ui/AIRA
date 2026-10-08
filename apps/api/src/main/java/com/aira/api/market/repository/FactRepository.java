package com.aira.api.market.repository;

import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.FactPredicate;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FactRepository extends JpaRepository<Fact, UUID> {
    Optional<Fact> findByDedupKey(byte[] dedupKey);

    List<Fact> findBySubjectEntityIdAndPredicateInAndPeriodStartAndPeriodEnd(
            UUID subjectEntityId, Collection<FactPredicate> predicates,
            LocalDate periodStart, LocalDate periodEnd);

    @Query("""
            select distinct fact
            from FactAssertion assertion
            join assertion.fact fact
            join assertion.evidence evidence
            join evidence.source source
            where fact.subjectEntity.id = :companyId
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
            """)
    List<Fact> findPubliclyAvailableFacts(@Param("companyId") UUID companyId);
}
