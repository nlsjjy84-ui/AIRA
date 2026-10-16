package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 기사 31문장 실험(2026-10-13)에서 못 찾은 금융사 이름을 거래소 약칭으로 이어 준다. */
class CompanyNameVariantsTests {

    @Test
    void keepsTheOriginalNameFirst() {
        assertEquals("삼성전자", CompanyNameVariants.of("삼성전자").get(0));
        assertEquals(List.of(), CompanyNameVariants.of("  "));
        assertEquals(List.of(), CompanyNameVariants.of(null));
    }

    @Test
    void mapsArticleNamesOfFinancialGroupsToKrxShortNames() {
        assertTrue(CompanyNameVariants.of("KB금융지주").contains("KB금융"));
        assertTrue(CompanyNameVariants.of("KB금융그룹").contains("KB금융"));
        assertTrue(CompanyNameVariants.of("신한금융지주").contains("신한지주"));
        assertTrue(CompanyNameVariants.of("신한금융그룹").contains("신한지주"));
        assertTrue(CompanyNameVariants.of("하나금융그룹").contains("하나금융지주"));
        assertTrue(CompanyNameVariants.of("하나금융").contains("하나금융지주"));
        assertTrue(CompanyNameVariants.of("우리금융그룹").contains("우리금융지주"));
        assertTrue(CompanyNameVariants.of("IBK기업은행").contains("기업은행"));
        assertTrue(CompanyNameVariants.of("DGB금융지주").contains("iM금융지주"), "renamed in 2024");
        assertTrue(CompanyNameVariants.of("삼성화재해상보험").contains("삼성화재"));
        assertTrue(CompanyNameVariants.of("현대해상화재보험").contains("현대해상"));
    }

    @Test
    void dropsCompanyFormMarksAndSpaces() {
        assertTrue(CompanyNameVariants.of("(주)카카오뱅크").contains("카카오뱅크"));
        assertTrue(CompanyNameVariants.of("주식회사 카카오뱅크").contains("카카오뱅크"));
        assertTrue(CompanyNameVariants.of("KB 금융지주").contains("KB금융"));
    }

    @Test
    void neverTurnsAWholeGroupIntoOneCompany() {
        assertFalse(CompanyNameVariants.of("SK그룹").contains("SK"));
        assertFalse(CompanyNameVariants.of("삼성그룹").contains("삼성"));
    }
}
