package com.aira.api.personalfinance.domain;

import com.aira.api.user.domain.AppUser;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "monthly_budget")
/**
 * Budget explicitly chosen by the user for one month/category.
 * AIRA may compare spending with this value, but AI/rules do not create an "appropriate" budget for the user.
 */
public class MonthlyBudget {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "budget_month", nullable = false)
    private LocalDate budgetMonth;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private BudgetCategory category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MonthlyBudget() {}

    public static MonthlyBudget create(AppUser user, LocalDate budgetMonth, BudgetCategory category,
            BigDecimal amount, String currencyCode, OffsetDateTime now) {
        if (user == null || budgetMonth == null || category == null || now == null) {
            throw new IllegalArgumentException("Budget owner, month, category and time are required");
        }
        if (budgetMonth.getDayOfMonth() != 1) {
            throw new IllegalArgumentException("Budget month must be the first day of a month");
        }
        if (amount == null || amount.signum() <= 0 || currencyCode == null || !currencyCode.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Positive budget amount and valid currency are required");
        }
        MonthlyBudget budget = new MonthlyBudget();
        budget.user = user;
        budget.budgetMonth = budgetMonth;
        budget.category = category;
        budget.amount = amount;
        budget.currencyCode = currencyCode;
        budget.createdAt = now;
        budget.updatedAt = now;
        return budget;
    }

    public UUID getId() { return id; }
    public AppUser getUser() { return user; }
    public LocalDate getBudgetMonth() { return budgetMonth; }
    public BudgetCategory getCategory() { return category; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrencyCode() { return currencyCode; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
