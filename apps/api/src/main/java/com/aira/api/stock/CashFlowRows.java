package com.aira.api.stock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 전체 재무제표 응답에서 현금흐름표(sj_div=CF)의 큰 줄기만 꺼낸다: 영업·투자·재무활동 현금흐름, 유형자산 취득, 기말 현금.
 * 표준 계정ID(ifrs-full_...)를 먼저 보고, 없으면 계정 이름으로 찾는다.
 */
final class CashFlowRows {
    private CashFlowRows() {}

    record Rule(String key, String accountId, String nameStarts) {}

    static final List<Rule> RULES = List.of(
            new Rule("OPERATING", "ifrs-full_CashFlowsFromUsedInOperatingActivities", "영업활동"),
            new Rule("INVESTING", "ifrs-full_CashFlowsFromUsedInInvestingActivities", "투자활동"),
            new Rule("FINANCING", "ifrs-full_CashFlowsFromUsedInFinancingActivities", "재무활동"),
            new Rule("CAPEX", "ifrs-full_PurchaseOfPropertyPlantAndEquipmentClassifiedAsInvestingActivities", "유형자산의취득"),
            new Rule("CASH_END", "ifrs-full_CashAndCashEquivalents", "기말현금"));

    static List<Map<String, Object>> convert(List<Map<String, String>> rows, int year) {
        Map<Integer, Map<String, BigDecimal>> byYear = new LinkedHashMap<>();
        for (Rule rule : RULES) {
            Map<String, String> found = null;
            for (Map<String, String> row : rows) {
                if (!"CF".equals(row.get("sj_div"))) continue;
                if (rule.accountId().equals(row.get("account_id"))) { found = row; break; }
            }
            if (found == null) {
                for (Map<String, String> row : rows) {
                    if (!"CF".equals(row.get("sj_div"))) continue;
                    String name = FinancialHistoryRows.normalize(row.get("account_nm"));
                    if (name.startsWith(rule.nameStarts()) || ("CASH_END".equals(rule.key()) && name.contains("기말") && name.contains("현금"))) { found = row; break; }
                }
            }
            if (found == null) continue;
            put(byYear, year, rule.key(), found.get("thstrm_amount"));
            put(byYear, year - 1, rule.key(), found.get("frmtrm_amount"));
            put(byYear, year - 2, rule.key(), found.get("bfefrmtrm_amount"));
        }
        List<Map<String, Object>> out = new ArrayList<>();
        byYear.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            if (e.getValue().get("OPERATING") == null) return;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("year", e.getKey());
            Map<String, Object> values = new LinkedHashMap<>(e.getValue());
            BigDecimal op = e.getValue().get("OPERATING");
            BigDecimal capex = e.getValue().get("CAPEX");
            if (op != null && capex != null) values.put("FREE_CASH_FLOW", op.subtract(capex.abs()));
            item.put("values", values);
            out.add(item);
        });
        return out;
    }

    private static void put(Map<Integer, Map<String, BigDecimal>> byYear, int year, String key, String raw) {
        BigDecimal value = KrxDailyRows.number(raw);
        if (value != null) byYear.computeIfAbsent(year, y -> new LinkedHashMap<>()).put(key, value);
    }
}
