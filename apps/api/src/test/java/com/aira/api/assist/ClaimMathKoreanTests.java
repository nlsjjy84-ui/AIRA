package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** 30문장 실험(2026-10-13)에서 나온 오판을 다시 막는다. */
class ClaimMathKoreanTests {
    private static String read(String text) {
        ClaimMath.Korean k = ClaimMath.koreanAmount(text);
        return k == null ? null : k.amount().toPlainString() + k.unit();
    }

    @Test
    void readsCompoundKoreanAmountsExactly() {
        assertEquals("472063억원", read("SK하이닉스는 2025년 영업이익 47조2063억원을 기록했다"));
        assertEquals("971467억원", read("SK하이닉스 2025년 매출은 97조 1천467억 원이다"));
        assertEquals("472063억원", read("영업이익은 47조 2천63억 원이다"));
        assertEquals("10544억원", read("크래프톤의 2025년 영업이익은 1조544억원이다"));
        assertEquals("10007억원", read("영업이익은 1조7억원이다"));
        assertEquals("11809억원", read("영업이익 1조 1,809억원"));
        assertEquals("9768억원", read("영업이익은 9768억원이다"));
        assertEquals("43.6조원", read("삼성전자 영업이익 43.6조"));
        assertEquals("400조원", read("작년 매출이 400조 원을 넘었다던데?"));
        assertEquals("15000억원", read("1조5천억원"));
        assertEquals("43601051000000원", read("43,601,051,000,000원"));
        assertNull(read("2025년 영업이익이 크게 늘었다"), "a year is not an amount");
        assertNull(read("매출 97조, 영업이익 47조"), "two amounts: let the AI split them");
    }

    @Test
    void fullWonAmountsUseTheirTrailingZerosAsTheRoundingStep() {
        BigDecimal official = new BigDecimal("47206319000000");
        assertEquals(ClaimMath.Closeness.MATCH, ClaimMath.compare(new BigDecimal("47206300000000"), BigDecimal.ONE, official));
        assertEquals(ClaimMath.Closeness.MISMATCH, ClaimMath.compare(new BigDecimal("47106300000000"), BigDecimal.ONE, official));
        assertEquals(ClaimMath.Closeness.MATCH, ClaimMath.compare(new BigDecimal("472063"), new BigDecimal("100000000"), official));
        assertEquals(ClaimMath.Closeness.MISMATCH, ClaimMath.compare(new BigDecimal("11795"), new BigDecimal("100000000"), new BigDecimal("1231926775791")),
                "1조1795억 is not 1.23조 (the old rule read it as 1조 and said MATCH)");
    }
}
