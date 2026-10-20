package com.aira.api.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DemoInterestSeedTests {
    private final UUID nh = UUID.randomUUID();
    private final UUID kb = UUID.randomUUID();
    private final UUID samsung = UUID.randomUUID();

    @Test
    void addsOnlyMissingCompaniesSoTheFirstPreferredEndsUpOnTop() {
        var available = Map.of("NH투자증권", nh, "KB금융", kb, "삼성전자", samsung);
        var toAdd = DemoLoginService.companiesToAdd(List.of("NH투자증권", "KB금융", "없는회사", "삼성전자"),
                available, Set.of(samsung));
        assertEquals(List.of(kb, nh), toAdd);
    }

    @Test
    void nothingToAddWhenEverythingIsSaved() {
        var toAdd = DemoLoginService.companiesToAdd(List.of("NH투자증권"), Map.of("NH투자증권", nh), Set.of(nh));
        assertTrue(toAdd.isEmpty());
    }

    @Test
    void demoListLeadsWithFinancialCompaniesAndKeepsTheOriginalFour() {
        assertEquals("NH투자증권", DemoLoginService.PREFERRED_COMPANIES.get(0));
        assertTrue(DemoLoginService.PREFERRED_COMPANIES.containsAll(List.of("삼성전자", "SK하이닉스", "NAVER", "현대자동차")));
        assertEquals(DemoLoginService.PREFERRED_COMPANIES.size(), Set.copyOf(DemoLoginService.PREFERRED_COMPANIES).size());
    }
}
