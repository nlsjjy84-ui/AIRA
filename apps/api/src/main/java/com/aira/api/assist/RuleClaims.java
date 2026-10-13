package com.aira.api.assist;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * AI 없이 쓰는 문장 나누기. "회사 (연도) 항목 금액단위" 꼴의 간단한 말만 골라 AI와 같은 모양(company·item·year·amount·unit)으로 만든다.
 * 회사 이름은 문장 첫 낱말(조사 제거)로 보고, 실제 회사인지는 판정 단계에서 저장된 회사 목록으로 다시 확인한다.
 */
final class RuleClaims {
    /** 문장 끝: 마침표(소수점 43.6의 점은 제외)·물음표·느낌표·줄바꿈. */
    private static final Pattern SENTENCE_END = Pattern.compile("(?<!\\d)\\.|\\.(?!\\d)|[!?\\n]");
    private static final Pattern ITEM = Pattern.compile("(매출액|매출|영업이익|영업손실|당기순이익|순이익|순손실|자산\\s*총계|총자산|자산|부채\\s*총계|총부채|부채|자본\\s*총계|자본)");
    private static final Pattern YEAR = Pattern.compile("(20\\d{2})\\s*년");
    private static final Pattern PARTICLE = Pattern.compile("(은|는|이|가|의|도)$");

    private RuleClaims() {}

    static List<JsonNode> split(String text, ObjectMapper json) {
        List<JsonNode> out = new ArrayList<>();
        for (String part : SENTENCE_END.split(text == null ? "" : text)) {
            if (out.size() >= ClaimCheckService.MAX_CLAIMS) break;
            String sentence = part.strip();
            if (sentence.length() < 4) continue;
            Matcher item = ITEM.matcher(sentence);
            if (!item.find()) continue;
            String after = sentence.substring(item.end());
            if (after.contains("%")) continue;
            ClaimMath.Korean amount = ClaimMath.koreanAmount(after);
            if (amount == null) continue;
            String first = sentence.split("\\s+")[0];
            String company = PARTICLE.matcher(first).replaceAll("");
            if (company.isBlank() || ITEM.matcher(company).find()) continue;
            ObjectNode claim = json.createObjectNode();
            claim.put("text", sentence);
            claim.put("kind", "FACT");
            claim.put("company", company);
            claim.put("item", code(item.group(1)));
            Matcher year = YEAR.matcher(sentence);
            if (year.find()) claim.put("year", Integer.parseInt(year.group(1)));
            else claim.putNull("year");
            claim.put("amount", amount.amount().toPlainString());
            claim.put("unit", amount.unit());
            claim.put("change", "");
            out.add(claim);
        }
        return out;
    }

    static String code(String word) {
        String w = word.replaceAll("\\s+", "");
        return switch (w) {
            case "매출", "매출액" -> "REVENUE";
            case "영업이익", "영업손실" -> "OPERATING_INCOME";
            case "순이익", "당기순이익", "순손실" -> "NET_INCOME";
            case "자산", "자산총계", "총자산" -> "TOTAL_ASSETS";
            case "부채", "부채총계", "총부채" -> "TOTAL_LIABILITIES";
            default -> "TOTAL_EQUITY";
        };
    }
}
