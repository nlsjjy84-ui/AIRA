package com.aira.api.personalfinance.repository;

import com.aira.api.personalfinance.domain.MonthlyBudget;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MonthlyBudgetRepository extends JpaRepository<MonthlyBudget, UUID> {
    List<MonthlyBudget> findAllByUser_IdAndBudgetMonthOrderByCategory(UUID userId, LocalDate budgetMonth);
}
