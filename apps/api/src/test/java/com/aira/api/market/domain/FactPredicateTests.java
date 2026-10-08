package com.aira.api.market.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class FactPredicateTests {
    @Test
    void companyFinancialsAreTheSixDisclosureItemsAndNothingElse() {
        assertEquals(Set.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME, FactPredicate.NET_INCOME,
                FactPredicate.TOTAL_ASSETS, FactPredicate.TOTAL_LIABILITIES, FactPredicate.TOTAL_EQUITY),
                FactPredicate.COMPANY_FINANCIALS);
        assertTrue(FactPredicate.TOTAL_EQUITY.isCompanyFinancial());
        assertFalse(FactPredicate.CLOSE_PRICE.isCompanyFinancial());
        assertFalse(FactPredicate.REAL_GDP.isCompanyFinancial());
    }

    @Test
    void coreFinancialsKeepTheOriginalTwoItems() {
        assertEquals(Set.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME), FactPredicate.CORE_FINANCIALS);
    }

    @Test
    void everyPredicateNameFitsTheDatabaseColumn() {
        for (FactPredicate predicate : FactPredicate.values()) {
            assertTrue(predicate.name().length() <= 32, predicate.name());
        }
    }
}
