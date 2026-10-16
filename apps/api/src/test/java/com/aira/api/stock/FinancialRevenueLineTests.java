package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** 금융회사 수익 줄은 회사가 적은 이름 그대로 찾는다. 번호·괄호·빈칸만 지운다. */
class FinancialRevenueLineTests {
    @Test
    void onlyNumberingBracketsAndSpacesAreRemovedFromTheAccountName() {
        assertEquals("영업수익", StockPageService.plainAccountName("Ⅰ. 영업수익(주석 25)"));
        assertEquals("이자수익", StockPageService.plainAccountName("1. 이자 수익"));
        assertEquals("순이자이익", StockPageService.plainAccountName("순이자이익"));
        assertEquals("", StockPageService.plainAccountName(null));
    }
}
