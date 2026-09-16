package com.aira.api.personalfinance.repository;

import com.aira.api.personalfinance.domain.PersonalFinanceTransaction;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalFinanceTransactionRepository extends JpaRepository<PersonalFinanceTransaction, UUID> {
    List<PersonalFinanceTransaction> findAllByUser_IdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDesc(
            UUID userId, OffsetDateTime fromInclusive, OffsetDateTime toExclusive);
}
