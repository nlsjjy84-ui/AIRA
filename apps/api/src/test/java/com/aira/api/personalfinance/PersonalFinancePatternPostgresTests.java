package com.aira.api.personalfinance;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.personalfinance.domain.BudgetCategory;
import com.aira.api.personalfinance.domain.SpendingChangeDirection;
import com.aira.api.personalfinance.service.PersonalFinancePatternService;
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
class PersonalFinancePatternPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PersonalFinancePatternService patterns;

    @Test
    void ruleAnalysisComparesMonthsAndLinksExactTransactionEvidence() {
        UUID owner = createUser("PatternOwner");
        UUID other = createUser("PatternOther");
        UUID ownerAccount = createAccount(owner, "21");
        UUID otherAccount = createAccount(other, "22");
        try {
            UUID previousFood = insertExpense(owner, ownerAccount,
                    "2026-08-10T03:00:00Z", "FOOD", "10000", "KRW", "31");
            UUID currentFood = insertExpense(owner, ownerAccount,
                    "2026-09-10T03:00:00Z", "FOOD", "15000", "KRW", "32");
            UUID currentTransport = insertExpense(owner, ownerAccount,
                    "2026-09-11T03:00:00Z", "TRANSPORT", "5000", "KRW", "33");
            insertExpense(owner, ownerAccount,
                    "2026-09-12T03:00:00Z", "TRANSFER", "70000", "KRW", "34");
            insertExpense(owner, ownerAccount,
                    "2026-09-13T03:00:00Z", "FOOD", "999", "USD", "35");
            insertExpense(other, otherAccount,
                    "2026-09-14T03:00:00Z", "FOOD", "80000", "KRW", "36");

            var result = patterns.analyze(owner, "2026-09", "KRW");
            assertEquals("RULE", result.method());
            assertEquals("2026-09", result.month());
            assertEquals("2026-08", result.previousMonth());
            assertEquals(new BigDecimal("20000.00"), result.total().currentSpent());
            assertEquals(new BigDecimal("10000.00"), result.total().previousSpent());
            assertEquals(new BigDecimal("10000.00"), result.total().delta());
            assertEquals(new BigDecimal("100.00"), result.total().percentChange());
            assertEquals(SpendingChangeDirection.INCREASED, result.total().direction());
            assertTrue(result.total().currentTransactionIds().containsAll(
                    java.util.List.of(currentFood, currentTransport)));
            assertEquals(java.util.List.of(previousFood), result.total().previousTransactionIds());
            var food = result.categories().stream()
                    .filter(item -> item.scope() == BudgetCategory.FOOD).findFirst().orElseThrow();
            assertEquals(new BigDecimal("15000.00"), food.currentSpent());
            assertEquals(new BigDecimal("10000.00"), food.previousSpent());
            assertEquals(new BigDecimal("5000.00"), food.delta());
            assertEquals(new BigDecimal("50.00"), food.percentChange());
            assertEquals(java.util.List.of(currentFood), food.currentTransactionIds());
            assertEquals(java.util.List.of(previousFood), food.previousTransactionIds());

            var transport = result.categories().stream()
                    .filter(item -> item.scope() == BudgetCategory.TRANSPORT).findFirst().orElseThrow();
            assertEquals(new BigDecimal("5000.00"), transport.currentSpent());
            assertEquals(BigDecimal.ZERO, transport.previousSpent());
            assertNull(transport.percentChange());
            assertEquals("PREVIOUS_PERIOD_ZERO", transport.unknownReason());
            assertEquals(SpendingChangeDirection.INCREASED, transport.direction());
            assertEquals(java.util.List.of(currentTransport), transport.currentTransactionIds());
            assertFalse(result.limitations().isEmpty());
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id IN (?,?)", owner, other);
        }
    }

    private UUID createUser(String prefix) {
        UUID id = UUID.randomUUID();
        String nickname = prefix + id.toString().replace("-", "").substring(0, 8);
        jdbc.update("""
                INSERT INTO app_user(id,nickname,nickname_normalized,status,timezone)
                VALUES (?,?,?,'ACTIVE','Asia/Seoul')
                """, id, nickname, nickname.toLowerCase());
        return id;
    }

    private UUID createAccount(UUID userId, String hex) {
        UUID connection = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO personal_finance_connection(
                    id,user_id,source_type,provider_key,display_name,status,consented_at)
                VALUES (?,?,'DEMO_IMPORT',?,'Pattern test','ACTIVE',CURRENT_TIMESTAMP)
                """, connection, userId, "pattern-test-" + hex);
        jdbc.update("""
                INSERT INTO personal_finance_account(
                    id,user_id,connection_id,account_type,display_name,currency_code,source_ref_hash)
                VALUES (?,?,?,'CHECKING','Pattern test account','KRW',decode(?,'hex'))
                """, account, userId, connection, hex + hex);
        return account;
    }

    private UUID insertExpense(UUID userId, UUID accountId, String occurredAt,
            String category, String amount, String currency, String hex) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO personal_finance_transaction(
                    id,user_id,account_id,occurred_at,direction,amount,currency_code,
                    merchant_name,category,source_ref_hash)
                VALUES (?,?,?,?,'EXPENSE',?,?, 'Pattern test merchant',?,decode(?,'hex'))
                """, id, userId, accountId, OffsetDateTime.parse(occurredAt),
                new BigDecimal(amount), currency, category, hex + hex + hex);
        return id;
    }
}
