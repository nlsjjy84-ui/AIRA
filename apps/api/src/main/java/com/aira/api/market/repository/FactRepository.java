package com.aira.api.market.repository;

import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.FactPredicate;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactRepository extends JpaRepository<Fact, UUID> {
    Optional<Fact> findByDedupKey(byte[] dedupKey);

    List<Fact> findBySubjectEntityIdAndPredicateInAndPeriodStartAndPeriodEnd(
            UUID subjectEntityId, Collection<FactPredicate> predicates,
            LocalDate periodStart, LocalDate periodEnd);
}
