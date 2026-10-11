package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ClaimMathTests {
    private static final BigDecimal OFFICIAL = new BigDecimal("43601051000000");

    private static ClaimMath.Closeness cmp(String amount, String unit) {
        return ClaimMath.compare(ClaimMath.amount(amount), ClaimMath.factor(unit), OFFICIAL);
    }

    @Test
    void roundedStatementMatches() {
        assertEquals(ClaimMath.Closeness.MATCH, cmp("43.6", "조원"));
        assertEquals(ClaimMath.Closeness.MATCH, cmp("43,601,051,000,000", "원"));
        assertEquals(ClaimMath.Closeness.MATCH, cmp("436011", "억원"));
    }

    @Test
    void oneStepOffIsApproximate() {
        assertEquals(ClaimMath.Closeness.APPROX, cmp("43", "조원"));
        assertEquals(ClaimMath.Closeness.APPROX, cmp("43.7", "조원"));
        assertEquals(ClaimMath.Closeness.APPROX, cmp("436010", "억원"));
    }

    @Test
    void clearlyDifferentIsMismatch() {
        assertEquals(ClaimMath.Closeness.MISMATCH, cmp("50", "조원"));
        assertEquals(ClaimMath.Closeness.MISMATCH, cmp("4.36", "조원"));
        assertEquals(ClaimMath.Closeness.MISMATCH, ClaimMath.compare(new BigDecimal("-1"), ClaimMath.factor("조원"), OFFICIAL));
    }

    @Test
    void parsesOnlyPlainNumbers() {
        assertEquals(new BigDecimal("1234"), ClaimMath.amount("1,234"));
        assertNull(ClaimMath.amount("약 12"));
        assertNull(ClaimMath.amount(""));
        assertNull(ClaimMath.amount(null));
        assertNull(ClaimMath.factor("달러"));
        assertNull(ClaimMath.factor(null));
    }

    @Test
    void formatsLargeAmounts() {
        assertEquals("43.60조 원 (43,601,051,000,000원)", ClaimMath.format(OFFICIAL));
        assertEquals("-2,500억 원 (-250,000,000,000원)", ClaimMath.format(new BigDecimal("-250000000000")));
        assertEquals("1,234원", ClaimMath.format(new BigDecimal("1234")));
    }
}
