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
        // 원 단위로 풀어 쓴 큰 금액(47,206,300,000,000원)은 끝의 0이 "그 자리까지 반올림했다"는 뜻이다(=472,063억 원).
        if (factor.compareTo(BigDecimal.ONE) == 0 && scale == 0 && statedAmount.signum() != 0) {
            int zeros = trailingZeros(statedAmount);
            if (zeros >= 4) step = BigDecimal.ONE.movePointRight(zeros);
        }
        BigDecimal diff = stated.subtract(official).abs();
        if (diff.compareTo(step.divide(BigDecimal.valueOf(2))) <= 0) return Closeness.MATCH;
        if (diff.compareTo(step) <= 0) return Closeness.APPROX;
        return Closeness.MISMATCH;
    }

    static int trailingZeros(BigDecimal integer) {
        String digits = integer.abs().toBigInteger().toString();
        int zeros = 0;
        for (int i = digits.length() - 1; i > 0 && digits.charAt(i) == '0'; i--) zeros++;
        return zeros;
    }

    /** 한글 금액 하나: 금액과 단위(조원·억원·만원·원). "47조2063억" → 472063 억원, "97조 1천467억" → 971467 억원, "43.6조" → 43.6 조원. */
    record Korean(BigDecimal amount, String unit) {}

    private static final Pattern KOREAN = Pattern.compile(
            "(-?\\d[\\d,]*(?:\\.\\d+)?)\\s*조(?:\\s*((?:\\d+\\s*천\\s*)?\\d[\\d,]*|\\d+\\s*천)\\s*억)?\\s*원?"
            + "|(-?(?:\\d+\\s*천\\s*)?\\d[\\d,]*(?:\\.\\d+)?|-?\\d+\\s*천)\\s*억\\s*원?"
            + "|(-?\\d[\\d,]*(?:\\.\\d+)?)\\s*만\\s*원"
            + "|(-?\\d[\\d,]{4,}(?:\\.\\d+)?)\\s*원");

    /** 글에 한글 금액이 꼭 하나 있으면 그 값, 없거나 둘 이상이면 null(그때는 AI가 나눈 값을 쓴다). */
    static Korean koreanAmount(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = KOREAN.matcher(text);
        Korean found = null;
        while (m.find()) {
            if (found != null) return null;
            if (m.group(1) != null) {
                BigDecimal jo = plain(m.group(1));
                if (m.group(2) == null) found = new Korean(jo, "조원");
                else if (jo.scale() > 0) return null;
                else found = new Korean(jo.multiply(BigDecimal.valueOf(10_000L)).add(cheon(m.group(2))), "억원");
            } else if (m.group(3) != null) found = new Korean(cheon(m.group(3)), "억원");
            else if (m.group(4) != null) found = new Korean(plain(m.group(4)), "만원");
            else found = new Korean(plain(m.group(5)), "원");
        }
        return found;
    }

    private static BigDecimal plain(String digits) { return new BigDecimal(digits.replace(",", "").replaceAll("\\s", "")); }

    /** "9천479" → 9479, "2천63" → 2063, "5천" → 5000, "1,809" → 1809, "43.5" → 43.5 */
    private static BigDecimal cheon(String text) {
        String t = text.replace(",", "").replaceAll("\\s", "");
        int at = t.indexOf('천');
        if (at < 0) return new BigDecimal(t);
        BigDecimal thousands = new BigDecimal(t.substring(0, at)).multiply(BigDecimal.valueOf(1000));
        String rest = t.substring(at + 1);
        return rest.isEmpty() ? thousands : thousands.add(new BigDecimal(rest));
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
