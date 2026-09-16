package com.aira.api.personalfinance;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.personalfinance.domain.BudgetCategory;
import com.aira.api.personalfinance.dto.BudgetUpsertRequest;
import com.aira.api.personalfinance.service.PersonalFinanceBudgetService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class PersonalFinanceBudgetPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PersonalFinanceBudgetService budgets;

    @Test
    void budgetUpsertReusesOneOwnedBudgetRow() {
        UUID user = createUser("BudgetOwner", "Asia/Seoul");
        try {
            var first = budgets.upsert(user, new BudgetUpsertRequest(
                    "2026-09", BudgetCategory.TOTAL, new BigDecimal("1500000"), "KRW"));
            var second = budgets.upsert(user, new BudgetUpsertRequest(
                    "2026-09", BudgetCategory.TOTAL, new BigDecimal("1650000"), "KRW"));
            assertEquals(first.id(), second.id());
            assertEquals(new BigDecimal("1650000.00"), second.amount());
            assertEquals(1, jdbc.queryForObject("""
                    SELECT count(*) FROM monthly_budget
                    WHERE user_id=? AND budget_month='2026-09-01' AND category='TOTAL' AND currency_code='KRW'
                    """, Integer.class, user));
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id=?", user);
        }
    }

    @Test
    void monthlySummaryUsesUserTimezoneAndExcludesTransfersOtherUsersAndCurrencies() {
        UUID owner = createUser("SummaryOwner", "Asia/Seoul");
        UUID other = createUser("SummaryOther", "Asia/Seoul");
        UUID ownerAccount = createAccount(owner, "01");
        UUID otherAccount = createAccount(other, "02");
        try {
            budgets.upsert(owner, new BudgetUpsertRequest(
                    "2026-09", BudgetCategory.TOTAL, new BigDecimal("100000"), "KRW"));
            budgets.upsert(owner, new BudgetUpsertRequest(
                    "2026-09", BudgetCategory.FOOD, new BigDecimal("50000"), "KRW"));

            insertExpense(owner, ownerAccount, "2026-08-31T15:30:00Z", "FOOD", "20000", "KRW", "11");
            insertExpense(owner, ownerAccount, "2026-09-15T03:00:00Z", "FOOD", "10000", "KRW", "12");
            insertExpense(owner, ownerAccount, "2026-09-20T03:00:00Z", "TRANSFER", "30000", "KRW", "13");
            insertExpense(owner, ownerAccount, "2026-09-21T03:00:00Z", "FOOD", "999", "USD", "14");
            insertExpense(owner, ownerAccount, "2026-09-30T15:30:00Z", "FOOD", "40000", "KRW", "15");
            insertExpense(other, otherAccount, "2026-09-10T03:00:00Z", "FOOD", "80000", "KRW", "16");
            var summary = budgets.summary(owner, "2026-09", "KRW");
            assertEquals(new BigDecimal("30000.00"), summary.totalSpent());
            assertEquals(new BigDecimal("100000.00"), summary.totalBudget());
            assertEquals(new BigDecimal("70000.00"), summary.totalRemaining());
            assertEquals(1, summary.categories().size());
            assertEquals(BudgetCategory.FOOD, summary.categories().getFirst().category());
            assertEquals(new BigDecimal("30000.00"), summary.categories().getFirst().spent());
            assertEquals(new BigDecimal("50000.00"), summary.categories().getFirst().budget());
            assertEquals(new BigDecimal("20000.00"), summary.categories().getFirst().remaining());
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id IN (?,?)", owner, other);
        }
    }

    private UUID createUser(String prefix, String timezone) {
        UUID id = UUID.randomUUID();
        String nickname = prefix + id.toString().replace("-", "").substring(0, 8);
        jdbc.update("""
                INSERT INTO app_user(id,nickname,nickname_normalized,status,timezone)
                VALUES (?,?,?,'ACTIVE',?)
                """, id, nickname, nickname.toLowerCase(), timezone);
        return id;
    }

    private UUID createAccount(UUID userId, String hex) {
        UUID connection = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO personal_finance_connection(
                    id,user_id,source_type,provider_key,display_name,status,consented_at)
                VALUES (?,?,'DEMO_IMPORT',?,'Budget test','ACTIVE',CURRENT_TIMESTAMP)
                """, connection, userId, "budget-test-" + hex);
        jdbc.update("""
                INSERT INTO personal_finance_account(
                    id,user_id,connection_id,account_type,display_name,currency_code,source_ref_hash)
                VALUES (?,?,?,'CHECKING','Budget test account','KRW',decode(?,'hex'))
                """, account, userId, connection, hex + hex);
        return account;
    }

    private void insertExpense(UUID userId, UUID accountId, String occurredAt,
            String category, String amount, String currency, String hex) {
        jdbc.update("""
                INSERT INTO personal_finance_transaction(
                    id,user_id,account_id,occurred_at,direction,amount,currency_code,
                    merchant_name,category,source_ref_hash)
                VALUES (?,?,?,?,'EXPENSE',?,?, 'Budget test merchant',?,decode(?,'hex'))
                """, UUID.randomUUID(), userId, accountId, OffsetDateTime.parse(occurredAt),
                new BigDecimal(amount), currency, category, hex + hex + hex);
    }
}
