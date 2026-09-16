package com.aira.api.personalfinance;

import static org.junit.jupiter.api.Assertions.*;
import com.aira.api.personalfinance.domain.*;
import com.aira.api.user.domain.AppUser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PersonalFinanceDomainTests {
    private final OffsetDateTime now = OffsetDateTime.parse("2026-09-16T12:00:00+09:00");

    @Test
    void createsOneUserOwnedChainWithoutRawFinancialIdentifiers() {
        AppUser user = AppUser.create("financeUser", "financeuser", now);
        var connection = PersonalFinanceConnection.create(user, FinanceConnectionSource.DEMO_IMPORT,
                "demo-v1", "데모 금융데이터", now);
        byte[] accountHash = {1, 2, 3};
        var account = PersonalFinanceAccount.create(connection, FinanceAccountType.CARD,
                "생활 카드", "KRW", accountHash, now);
        byte[] transactionHash = {4, 5, 6};
        var transaction = PersonalFinanceTransaction.create(account, now.minusDays(1),
                FinanceTransactionDirection.EXPENSE, new BigDecimal("12000"), "KRW", "식당",
                FinanceTransactionCategory.FOOD, transactionHash, now);
        var budget = MonthlyBudget.create(user, LocalDate.of(2026, 9, 1), BudgetCategory.FOOD,
                new BigDecimal("300000"), "KRW", now);

        assertSame(user, connection.getUser());
        assertSame(user, account.getUser());
        assertSame(user, transaction.getUser());
        assertSame(user, budget.getUser());
        assertEquals(FinanceConnectionStatus.ACTIVE, connection.getStatus());
        assertArrayEquals(accountHash, account.getSourceRefHash());
        assertArrayEquals(transactionHash, transaction.getSourceRefHash());
        accountHash[0] = 9;
        transactionHash[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, account.getSourceRefHash());
        assertArrayEquals(new byte[]{4, 5, 6}, transaction.getSourceRefHash());
    }

    @Test
    void rejectsInvalidBudgetAndTransactionValues() {
        AppUser user = AppUser.create("financeTwo", "financetwo", now);
        var connection = PersonalFinanceConnection.create(user, FinanceConnectionSource.DEMO_IMPORT,
                "demo-v2", "데모 금융데이터", now);
        var account = PersonalFinanceAccount.create(connection, FinanceAccountType.CHECKING,
                "생활비", "KRW", new byte[]{1}, now);

        assertThrows(IllegalArgumentException.class, () -> PersonalFinanceTransaction.create(account, now,
                FinanceTransactionDirection.EXPENSE, BigDecimal.ZERO, "KRW", "상점",
                FinanceTransactionCategory.SHOPPING, new byte[]{2}, now));
        assertThrows(IllegalArgumentException.class, () -> PersonalFinanceTransaction.create(account, now,
                FinanceTransactionDirection.EXPENSE, BigDecimal.ONE, "KRW", "상점",
                FinanceTransactionCategory.INCOME, new byte[]{3}, now));
        assertThrows(IllegalArgumentException.class, () -> MonthlyBudget.create(user,
                LocalDate.of(2026, 9, 2), BudgetCategory.TOTAL, BigDecimal.TEN, "KRW", now));
        assertThrows(IllegalArgumentException.class, () -> MonthlyBudget.create(user,
                LocalDate.of(2026, 9, 1), BudgetCategory.TOTAL, BigDecimal.ZERO, "KRW", now));
    }
}
