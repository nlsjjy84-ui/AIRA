package com.aira.api.assist;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

/** Pure number handling for statement checks: parsing, rounding tolerance and display. No I/O. */
final class ClaimMath {
    private static final Pattern AMOUNT = Pattern.compile("-?\\d{1,15}(\\.\\d{1,6})?");

    private ClaimMath() {}

    enum Closeness { MATCH, APPROX, MISMATCH }

    static BigDecimal factor(String unit) {
        if (unit == null) return null;
        return switch (unit.trim()) {
            case "원" -> BigDecimal.ONE;
            case "만원" -> BigDecimal.valueOf(10_000L);
            case "억원" -> BigDecimal.valueOf(100_000_000L);
            case "조원" -> BigDecimal.valueOf(1_000_000_000_000L);
            default -> null;
        };
    }

    /** Parses the amount exactly as written (commas allowed); null when it is not a plain number. */
    static BigDecimal amount(String text) {
        if (text == null) return null;
        String cleaned = text.replace(",", "").trim();
        return AMOUNT.matcher(cleaned).matches() ? new BigDecimal(cleaned) : null;
    }

    /**
     * MATCH when the stated number is within half a unit of its last written digit of the official value
     * (it is how the number would be rounded), APPROX within one unit (also what truncation produces).
     */
    static Closeness compare(BigDecimal statedAmount, BigDecimal factor, BigDecimal official) {
        BigDecimal stated = statedAmount.multiply(factor);
        int scale = Math.max(statedAmount.scale(), 0);
        BigDecimal step = factor.movePointLeft(scale);
        BigDecimal diff = stated.subtract(official).abs();
        if (diff.compareTo(step.divide(BigDecimal.valueOf(2))) <= 0) return Closeness.MATCH;
        if (diff.compareTo(step) <= 0) return Closeness.APPROX;
        return Closeness.MISMATCH;
    }

    /** Year-over-year change in percent, or null when the previous value is zero. Uses the size of the previous value. */
    static BigDecimal percentChange(BigDecimal previous, BigDecimal current) {
        if (previous.signum() == 0) return null;
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous.abs(), 6, RoundingMode.HALF_UP);
    }

    /** Same rounding tolerance as amounts, applied to a stated percent (signed). */
    static Closeness comparePercent(BigDecimal stated, BigDecimal actual) {
        int scale = Math.max(stated.scale(), 0);
        BigDecimal step = BigDecimal.ONE.movePointLeft(scale);
        BigDecimal diff = stated.subtract(actual).abs();
        if (diff.compareTo(step.divide(BigDecimal.valueOf(2))) <= 0) return Closeness.MATCH;
        if (diff.compareTo(step) <= 0) return Closeness.APPROX;
        return Closeness.MISMATCH;
    }

    static String formatPercent(BigDecimal percent) {
        return (percent.signum() > 0 ? "+" : "") + percent.setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
    }

    static String format(BigDecimal won) {
        BigDecimal abs = won.abs();
        String sign = won.signum() < 0 ? "-" : "";
        String exact = String.format("%s%,d원", sign, won.setScale(0, RoundingMode.HALF_UP).abs().longValue());
        if (abs.compareTo(BigDecimal.valueOf(1_000_000_000_000L)) >= 0) {
            return sign + abs.divide(BigDecimal.valueOf(1_000_000_000_000L), 2, RoundingMode.HALF_UP).toPlainString()
                    + "조 원 (" + exact + ")";
        }
        if (abs.compareTo(BigDecimal.valueOf(100_000_000L)) >= 0) {
            return sign + String.format("%,d억 원", abs.divide(BigDecimal.valueOf(100_000_000L), 0, RoundingMode.HALF_UP)
                    .longValue()) + " (" + exact + ")";
        }
        return exact;
    }
}
