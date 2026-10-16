package com.aira.api.assist;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 기사·보도자료가 쓰는 회사 이름을 AIRA에 저장된 거래소 약칭으로 이어 주는 후보 이름 목록.
 *
 * <p>AIRA의 회사 이름은 KRX 종목 약칭(예: KB금융, 신한지주, 기업은행)이지만, 기사는 KB금융지주·신한금융그룹·IBK기업은행처럼
 * 정식 명칭이나 그룹 이름을 쓴다(2026-10-13 기사 31문장 실험에서 금융사 5곳을 못 찾은 원인). 여기서는 이름을 '추측'하지 않고,
 * 정해 둔 규칙으로 후보만 만든다. 후보마다 정확히 일치하는 회사가 하나일 때만 쓰이므로, 엉뚱한 회사로 이어지지 않는다.</p>
 */
final class CompanyNameVariants {
    private CompanyNameVariants() {}

    /** 이름이 바뀌었거나 기사에서 흔히 다른 이름으로 부르는 경우. 왼쪽은 공백을 뺀 기사 표기, 오른쪽은 KRX 약칭. */
    static final Map<String, String> KNOWN = Map.ofEntries(
            Map.entry("dgb금융지주", "iM금융지주"),
            Map.entry("dgb금융그룹", "iM금융지주"),
            Map.entry("im금융그룹", "iM금융지주"),
            Map.entry("ibk기업은행", "기업은행"),
            Map.entry("중소기업은행", "기업은행"),
            Map.entry("신한금융지주", "신한지주"),
            Map.entry("신한금융지주회사", "신한지주"),
            Map.entry("신한금융그룹", "신한지주"),
            Map.entry("신한금융", "신한지주"),
            Map.entry("삼성화재해상보험", "삼성화재"),
            Map.entry("현대해상화재보험", "현대해상"),
            Map.entry("db손보", "DB손해보험"),
            Map.entry("한국투자금융지주", "한국금융지주"));

    /** 회사 형태 표기. 기사 이름 앞뒤에 붙어 있으면 떼고 찾는다. */
    private static final List<String> FORM_MARKS = List.of("주식회사", "(주)", "㈜", "（주）");

    /** 기사 이름의 끝을 거래소 약칭 쪽으로 바꿔 보는 규칙(앞의 것을 먼저 시도). */
    private static final List<String[]> SUFFIX_SWAPS = List.of(
            new String[] {"금융지주회사", "금융지주"},
            new String[] {"금융지주회사", "금융"},
            new String[] {"금융그룹", "금융지주"},
            new String[] {"금융그룹", "금융"},
            new String[] {"금융지주", "금융"},
            new String[] {"지주회사", "지주"});
    // '○○그룹' 전체를 떼는 규칙은 넣지 않는다. 그룹 전체 실적을 한 회사 실적과 비교하게 되기 때문이다.

    /**
     * 원래 이름을 맨 앞에 두고, 정리한 이름과 규칙으로 바꾼 이름을 차례로 돌려준다. 같은 이름은 한 번만 넣는다.
     * 빈 이름이나 너무 긴 이름은 원래 이름만 돌려준다.
     */
    static List<String> of(String name) {
        Set<String> out = new LinkedHashSet<>();
        if (name == null) return List.of();
        String original = name.strip();
        if (original.isEmpty()) return List.of();
        out.add(original);
        if (original.length() > 100) return new ArrayList<>(out);

        String cleaned = original;
        for (String mark : FORM_MARKS) cleaned = cleaned.replace(mark, "");
        cleaned = cleaned.replaceAll("\\s+", "");
        if (cleaned.isEmpty()) return new ArrayList<>(out);
        out.add(cleaned);

        String known = KNOWN.get(cleaned.toLowerCase(java.util.Locale.ROOT));
        if (known != null) out.add(known);

        for (String[] swap : SUFFIX_SWAPS) {
            if (cleaned.endsWith(swap[0]) && cleaned.length() > swap[0].length()) {
                out.add(cleaned.substring(0, cleaned.length() - swap[0].length()) + swap[1]);
            }
        }
        // 기사에 '하나금융'처럼 줄여 쓰고 거래소 약칭이 '하나금융지주'인 경우.
        if (cleaned.endsWith("금융") && cleaned.length() > 2) out.add(cleaned + "지주");
        return new ArrayList<>(out);
    }
}
