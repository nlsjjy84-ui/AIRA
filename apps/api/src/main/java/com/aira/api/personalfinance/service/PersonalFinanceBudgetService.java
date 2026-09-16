package com.aira.api.personalfinance.service;

import com.aira.api.personalfinance.domain.BudgetCategory;
import com.aira.api.personalfinance.dto.BudgetResponse;
import com.aira.api.personalfinance.dto.BudgetUpsertRequest;
import com.aira.api.personalfinance.dto.CategorySpendingResponse;
import com.aira.api.personalfinance.dto.MonthlySpendingSummaryResponse;
import com.aira.api.personalfinance.exception.InvalidPersonalFinanceRequestException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * User-authored budgets and deterministic monthly spending aggregation.
 * AIRA compares actual spending with the user's own budget; it does not invent an "appropriate" budget.
 */
@Service
public class PersonalFinanceBudgetService {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public PersonalFinanceBudgetService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }
    @Transactional
    public BudgetResponse upsert(UUID userId, BudgetUpsertRequest request) {
        if (userId == null || request == null) throw new InvalidPersonalFinanceRequestException();
        YearMonth month = parseMonth(request.month());
        String currency = normalizeCurrency(request.currencyCode());
        OffsetDateTime now = OffsetDateTime.now(clock);
        UUID id = UUID.randomUUID();

        return jdbc.queryForObject("""
                INSERT INTO monthly_budget(
                    id,user_id,budget_month,category,amount,currency_code,created_at,updated_at)
                VALUES (?,?,?,?,?,?,?,?)
                ON CONFLICT (user_id,budget_month,category,currency_code)
                DO UPDATE SET amount=EXCLUDED.amount, updated_at=EXCLUDED.updated_at
                RETURNING id,budget_month,category,amount,currency_code
                """, (rs, row) -> new BudgetResponse(
                        rs.getObject("id", UUID.class),
                        YearMonth.from(rs.getObject("budget_month", LocalDate.class)).toString(),
                        BudgetCategory.valueOf(rs.getString("category")),
                        rs.getBigDecimal("amount"), rs.getString("currency_code")),
                id, userId, month.atDay(1), request.category().name(), request.amount(),
                currency, now, now);
    }

    @Transactional(readOnly = true)
    public List<BudgetResponse> findBudgets(UUID userId, String monthValue, String currencyValue) {
        YearMonth month = parseMonth(monthValue);
        String currency = normalizeCurrency(currencyValue);
        return jdbc.query("""
                SELECT id,budget_month,category,amount,currency_code
                FROM monthly_budget
                WHERE user_id=? AND budget_month=? AND currency_code=?
                ORDER BY CASE WHEN category='TOTAL' THEN 0 ELSE 1 END, category
                """, (rs, row) -> new BudgetResponse(
                        rs.getObject("id", UUID.class),
                        YearMonth.from(rs.getObject("budget_month", LocalDate.class)).toString(),
                        BudgetCategory.valueOf(rs.getString("category")),
                        rs.getBigDecimal("amount"), rs.getString("currency_code")),
                userId, month.atDay(1), currency);
    }

    @Transactional(readOnly = true)
    public MonthlySpendingSummaryResponse summary(UUID userId, String monthValue, String currencyValue) {
        if (userId == null) throw new InvalidPersonalFinanceRequestException();
        YearMonth month = parseMonth(monthValue);
        String currency = normalizeCurrency(currencyValue);
        ZoneId zone = userZone(userId);
        OffsetDateTime start = month.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();

        Map<BudgetCategory, BigDecimal> spent = new EnumMap<>(BudgetCategory.class);
        jdbc.query("""
                SELECT category, COALESCE(SUM(amount),0) AS spent
                FROM personal_finance_transaction
                WHERE user_id=? AND direction='EXPENSE' AND currency_code=?
                  AND occurred_at>=? AND occurred_at<?
                  AND category NOT IN ('INCOME','TRANSFER')
                GROUP BY category
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    BudgetCategory category = BudgetCategory.valueOf(rs.getString("category"));
                    spent.put(category, rs.getBigDecimal("spent"));
                }, userId, currency, start, end);
        Map<BudgetCategory, BigDecimal> budgets = new EnumMap<>(BudgetCategory.class);
        jdbc.query("""
                SELECT category, amount
                FROM monthly_budget
                WHERE user_id=? AND budget_month=? AND currency_code=?
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> budgets.put(
                        BudgetCategory.valueOf(rs.getString("category")),
                        rs.getBigDecimal("amount")),
                userId, month.atDay(1), currency);

        BigDecimal totalSpent = spent.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBudget = budgets.get(BudgetCategory.TOTAL);
        BigDecimal totalRemaining = totalBudget == null ? null : totalBudget.subtract(totalSpent);

        List<CategorySpendingResponse> categories = new ArrayList<>();
        for (BudgetCategory category : BudgetCategory.values()) {
            if (category == BudgetCategory.TOTAL) continue;
            BigDecimal categorySpent = spent.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal categoryBudget = budgets.get(category);
            if (categorySpent.signum() == 0 && categoryBudget == null) continue;
            categories.add(new CategorySpendingResponse(
                    category,
                    categorySpent,
                    categoryBudget,
                    categoryBudget == null ? null : categoryBudget.subtract(categorySpent)));
        }
        return new MonthlySpendingSummaryResponse(
                month.toString(), currency, totalSpent, totalBudget, totalRemaining, categories);
    }

    private ZoneId userZone(UUID userId) {
        String configured = jdbc.queryForObject(
                "SELECT timezone FROM app_user WHERE id=?", String.class, userId);
        if (configured == null || configured.isBlank()) return DEFAULT_ZONE;
        try {
            return ZoneId.of(configured);
        } catch (RuntimeException invalidZone) {
            return DEFAULT_ZONE;
        }
    }
    private static YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (RuntimeException invalidMonth) {
            throw new InvalidPersonalFinanceRequestException();
        }
    }

    private static String normalizeCurrency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) {
            throw new InvalidPersonalFinanceRequestException();
        }
        return value;
    }
}
