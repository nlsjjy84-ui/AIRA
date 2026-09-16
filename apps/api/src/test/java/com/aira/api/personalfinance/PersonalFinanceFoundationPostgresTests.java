package com.aira.api.personalfinance;

import static org.junit.jupiter.api.Assertions.*;
import com.aira.api.personalfinance.domain.*;
import com.aira.api.personalfinance.repository.*;
import com.aira.api.user.repository.AppUserRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class PersonalFinanceFoundationPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired AppUserRepository users;
    @Autowired PersonalFinanceConnectionRepository connections;
    @Autowired PersonalFinanceAccountRepository accounts;
    @Autowired PersonalFinanceTransactionRepository transactions;
    @Autowired MonthlyBudgetRepository budgets;

    @Test
    void persistsUserOwnedFinanceChainAndCascadesWithUser() {
        UUID userId = insertUser("pfchain");
        OffsetDateTime now = OffsetDateTime.parse("2026-09-16T12:00:00+09:00");
        try {
            var user = users.findById(userId).orElseThrow();
            var connection = connections.saveAndFlush(PersonalFinanceConnection.create(user,
                    FinanceConnectionSource.DEMO_IMPORT, "demo-chain", "데모 금융데이터", now));
            var account = accounts.saveAndFlush(PersonalFinanceAccount.create(connection,
                    FinanceAccountType.CARD, "생활 카드", "KRW", new byte[]{1, 2, 3}, now));
            transactions.saveAndFlush(PersonalFinanceTransaction.create(account, now.minusDays(1),
                    FinanceTransactionDirection.EXPENSE, new BigDecimal("12000"), "KRW", "식당",
                    FinanceTransactionCategory.FOOD, new byte[]{4, 5, 6}, now));
            budgets.saveAndFlush(MonthlyBudget.create(user, LocalDate.of(2026, 9, 1), BudgetCategory.FOOD,
                    new BigDecimal("300000"), "KRW", now));

            assertEquals(1, count("personal_finance_connection", userId));
            assertEquals(1, count("personal_finance_account", userId));
            assertEquals(1, count("personal_finance_transaction", userId));
            assertEquals(1, count("monthly_budget", userId));

            jdbc.update("DELETE FROM app_user WHERE id=?", userId);
            assertEquals(0, count("personal_finance_connection", userId));
            assertEquals(0, count("personal_finance_account", userId));
            assertEquals(0, count("personal_finance_transaction", userId));
            assertEquals(0, count("monthly_budget", userId));
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id=?", userId);
        }
    }

    @Test
    void databaseBlocksCrossUserAccountLinkAndHasNoRawFinancialSecretColumns() {
        UUID ownerId = insertUser("pfownera");
        UUID otherId = insertUser("pfownerb");
        OffsetDateTime now = OffsetDateTime.parse("2026-09-16T12:00:00+09:00");
        try {
            var owner = users.findById(ownerId).orElseThrow();
            var connection = connections.saveAndFlush(PersonalFinanceConnection.create(owner,
                    FinanceConnectionSource.DEMO_IMPORT, "demo-owner", "데모 금융데이터", now));

            assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                    INSERT INTO personal_finance_account
                    (id,user_id,connection_id,account_type,display_name,currency_code,source_ref_hash)
                    VALUES(?,?,?,'CARD','잘못된 연결','KRW',?)
                    """, UUID.randomUUID(), otherId, connection.getId(), new byte[]{9}));

            Integer forbidden = jdbc.queryForObject("""
                    SELECT count(*) FROM information_schema.columns
                    WHERE table_schema='public'
                      AND table_name IN ('personal_finance_connection','personal_finance_account','personal_finance_transaction')
                      AND column_name IN ('account_number','card_number','access_token','refresh_token','credential')
                    """, Integer.class);
            assertEquals(0, forbidden);
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id IN (?,?)", ownerId, otherId);
        }
    }

    private UUID insertUser(String prefix) {
        UUID id = UUID.randomUUID();
        String nickname = prefix + id.toString().replace("-", "").substring(0, 6);
        jdbc.update("INSERT INTO app_user(id,nickname,nickname_normalized,status) VALUES(?,?,?,'ACTIVE')",
                id, nickname, nickname.toLowerCase());
        return id;
    }

    private int count(String table, UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?", Integer.class, userId);
    }
}
