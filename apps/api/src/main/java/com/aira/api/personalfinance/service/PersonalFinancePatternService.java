package com.aira.api.personalfinance.service;

import com.aira.api.personalfinance.domain.BudgetCategory;
import com.aira.api.personalfinance.domain.SpendingChangeDirection;
import com.aira.api.personalfinance.dto.MonthlySpendingPatternResponse;
import com.aira.api.personalfinance.dto.SpendingChangeResponse;
import com.aira.api.personalfinance.exception.InvalidPersonalFinanceRequestException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic personal-finance pattern analysis.
 * This is RULE output, not AI: every amount is computed from owned transactions and links back to them.
 */
@Service
public class PersonalFinancePatternService {
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final List<String> LIMITATIONS = List.of(
            "Transaction amounts and categories do not reveal why the user spent the money.",
            "AIRA does not label spending as good, bad, safe, or risky from this comparison alone.");

    private final JdbcTemplate jdbc;

    public PersonalFinancePatternService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }
    @Transactional(readOnly = true)
    public MonthlySpendingPatternResponse analyze(UUID userId, String monthValue, String currencyValue) {
        if (userId == null) throw new InvalidPersonalFinanceRequestException();
        YearMonth month = parseMonth(monthValue);
        String currency = normalizeCurrency(currencyValue);
        ZoneId zone = userZone(userId);

        PeriodData current = loadPeriod(userId, month, currency, zone);
        PeriodData previous = loadPeriod(userId, month.minusMonths(1), currency, zone);

        SpendingChangeResponse total = change(
                BudgetCategory.TOTAL,
                current.totalAmount(), previous.totalAmount(),
                current.allTransactionIds(), previous.allTransactionIds());

        LinkedHashSet<BudgetCategory> scopes = new LinkedHashSet<>();
        scopes.addAll(current.byCategory().keySet());
        scopes.addAll(previous.byCategory().keySet());
        List<SpendingChangeResponse> categories = new ArrayList<>();
        for (BudgetCategory scope : scopes) {
            CategoryData now = current.byCategory().getOrDefault(scope, CategoryData.empty());
            CategoryData before = previous.byCategory().getOrDefault(scope, CategoryData.empty());
            categories.add(change(scope, now.amount(), before.amount(), now.ids(), before.ids()));
        }

        return new MonthlySpendingPatternResponse(
                month.toString(), month.minusMonths(1).toString(), currency,
                "RULE", total, List.copyOf(categories), LIMITATIONS);
    }

    private PeriodData loadPeriod(UUID userId, YearMonth month, String currency, ZoneId zone) {
        OffsetDateTime start = month.atDay(1).atStartOfDay(zone).toOffsetDateTime();
        OffsetDateTime end = month.plusMonths(1).atDay(1).atStartOfDay(zone).toOffsetDateTime();
        Map<BudgetCategory, CategoryDataBuilder> builders = new EnumMap<>(BudgetCategory.class);
        List<UUID> allIds = new ArrayList<>();
        BigDecimal[] total = {BigDecimal.ZERO};
        jdbc.query("""
                SELECT id,category,amount
                FROM personal_finance_transaction
                WHERE user_id=? AND direction='EXPENSE' AND currency_code=?
                  AND occurred_at>=? AND occurred_at<?
                  AND category NOT IN ('INCOME','TRANSFER')
                ORDER BY occurred_at,id
                """, (org.springframework.jdbc.core.RowCallbackHandler) rs -> {
                    BudgetCategory category = BudgetCategory.valueOf(rs.getString("category"));
                    BigDecimal amount = rs.getBigDecimal("amount");
                    UUID id = rs.getObject("id", UUID.class);
                    total[0] = total[0].add(amount);
                    allIds.add(id);
                    builders.computeIfAbsent(category, ignored -> new CategoryDataBuilder())
                            .add(amount, id);
                }, userId, currency, start, end);

        Map<BudgetCategory, CategoryData> byCategory = new EnumMap<>(BudgetCategory.class);
        builders.forEach((category, builder) -> byCategory.put(category, builder.build()));
        return new PeriodData(total[0], List.copyOf(allIds), byCategory);
    }

    private static SpendingChangeResponse change(BudgetCategory scope,
            BigDecimal current, BigDecimal previous,
            List<UUID> currentIds, List<UUID> previousIds) {
        BigDecimal delta = current.subtract(previous);
        SpendingChangeDirection direction = delta.signum() > 0
                ? SpendingChangeDirection.INCREASED
                : delta.signum() < 0 ? SpendingChangeDirection.DECREASED : SpendingChangeDirection.STABLE;
        BigDecimal percent = previous.signum() == 0 ? null
                : delta.divide(previous, 6, RoundingMode.HALF_UP)
                        .multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
        String unknown = previous.signum() == 0 ? "PREVIOUS_PERIOD_ZERO" : null;
        return new SpendingChangeResponse(
                scope, current, previous, delta, percent, direction,
                List.copyOf(currentIds), List.copyOf(previousIds), unknown);
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

    private record PeriodData(
            BigDecimal totalAmount,
            List<UUID> allTransactionIds,
            Map<BudgetCategory, CategoryData> byCategory) {
    }
    private record CategoryData(BigDecimal amount, List<UUID> ids) {
        static CategoryData empty() {
            return new CategoryData(BigDecimal.ZERO, List.of());
        }
    }

    private static final class CategoryDataBuilder {
        private BigDecimal amount = BigDecimal.ZERO;
        private final List<UUID> ids = new ArrayList<>();

        void add(BigDecimal value, UUID id) {
            amount = amount.add(value);
            ids.add(id);
        }

        CategoryData build() {
            return new CategoryData(amount, List.copyOf(ids));
        }
    }
}
