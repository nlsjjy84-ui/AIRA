package com.aira.api.personalfinance.repository;

import com.aira.api.personalfinance.domain.PersonalFinanceAccount;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonalFinanceAccountRepository extends JpaRepository<PersonalFinanceAccount, UUID> {
    List<PersonalFinanceAccount> findAllByUser_IdOrderByCreatedAt(UUID userId);
}
