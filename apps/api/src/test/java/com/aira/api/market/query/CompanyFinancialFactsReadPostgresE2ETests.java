package com.aira.api.market.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.aira.api.market.domain.FactPredicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class CompanyFinancialFactsReadPostgresE2ETests {
    private static final UUID SAMSUNG_ID =
            UUID.fromString("5eafc0b5-c163-4cea-8dbd-131265004e95");

    @Autowired private CompanyFinancialFactsQuery query;

    @Test
    void readsExistingSamsungAnnualFactsWithoutWritingData() {
        var result = query.find(new CompanyFinancialFactsQueryInput(
                SAMSUNG_ID, Set.of("REVENUE", "OPERATING_INCOME"),
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31)));

        assertEquals(2, result.facts().size());
        var revenue = result.facts().stream()
                .filter(view -> view.predicate() == FactPredicate.REVENUE)
                .findFirst().orElseThrow();
        var operatingIncome = result.facts().stream()
                .filter(view -> view.predicate() == FactPredicate.OPERATING_INCOME)
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("333605938000000"), revenue.value());
        assertEquals(new BigDecimal("43601051000000"), operatingIncome.value());
        assertEquals("KRW", revenue.currency());
        assertEquals(LocalDate.of(2025, 1, 1), revenue.periodStart());
        assertEquals(LocalDate.of(2025, 12, 31), revenue.periodEnd());
        assertNull(revenue.publishedAt());
        assertEquals(revenue.collectedAt(), operatingIncome.collectedAt());
        assertEquals("OpenDART", revenue.sourceName());
        assertEquals("20260310002820", revenue.evidenceExternalId());
        assertEquals(revenue.evidenceId(), operatingIncome.evidenceId());
        assertEquals(revenue.evidenceOriginalUrl(), operatingIncome.evidenceOriginalUrl());
    }
}
