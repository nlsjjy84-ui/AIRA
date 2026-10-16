package com.aira.api.stock;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * OpenDART 다중회사 주요계정 응답을 (회사, 사업연도, 항목) 값으로 바꾼다.
 * 연결재무제표(CFS)가 있으면 연결을, 없을 때만 별도(OFS)를 쓴다. 한 보고서의 당기·전기·전전기 값을 모두 쓴다.
 */
final class FinancialHistoryRows {
    private FinancialHistoryRows() {}

    record Value(String corpCode, int fiscalYear, String account, BigDecimal amount, String fsDiv, String receiptNo, int reportYear) {}

    static final Map<String, String> ACCOUNTS = new LinkedHashMap<>();
    static {
        ACCOUNTS.put("매출액", "REVENUE");
        ACCOUNTS.put("수익(매출액)", "REVENUE");
        ACCOUNTS.put("영업수익", "REVENUE");
        ACCOUNTS.put("영업이익", "OPERATING_INCOME");
        ACCOUNTS.put("영업이익(손실)", "OPERATING_INCOME");
        ACCOUNTS.put("당기순이익", "NET_INCOME");
        ACCOUNTS.put("당기순이익(손실)", "NET_INCOME");
        ACCOUNTS.put("자산총계", "TOTAL_ASSETS");
        ACCOUNTS.put("부채총계", "TOTAL_LIABILITIES");
        ACCOUNTS.put("자본총계", "TOTAL_EQUITY");
        ACCOUNTS.put("유동자산", "CURRENT_ASSETS");
        ACCOUNTS.put("유동부채", "CURRENT_LIABILITIES");
    }

    static List<Value> convert(List<Map<String, String>> rows) { return convert(rows, null); }

    static List<Value> convert(List<Map<String, String>> rows, Integer requestedYear) {
        Set<String> hasConsolidated = new HashSet<>();
        for (Map<String, String> row : rows) {
            if ("CFS".equals(row.get("fs_div"))) hasConsolidated.add(row.get("corp_code"));
        }
        Map<String, Value> unique = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String corp = row.get("corp_code");
            String fs = row.get("fs_div");
            if (corp == null || !corp.matches("[0-9]{8}") || fs == null) continue;
            if (hasConsolidated.contains(corp) ? !"CFS".equals(fs) : !"OFS".equals(fs)) continue;
            String account = ACCOUNTS.get(normalize(row.get("account_nm")));
            if (account == null) continue;
            int year;
            try { year = Integer.parseInt(row.get("bsns_year").trim()); }
            catch (RuntimeException e) { if (requestedYear == null) continue; year = requestedYear; }
            String receipt = row.get("rcept_no");
            if (receipt == null || receipt.isBlank()) continue;
            add(unique, corp, year, account, row.get("thstrm_amount"), fs, receipt, year);
            add(unique, corp, year - 1, account, row.get("frmtrm_amount"), fs, receipt, year);
            add(unique, corp, year - 2, account, row.get("bfefrmtrm_amount"), fs, receipt, year);
        }
        return new ArrayList<>(unique.values());
    }

    private static void add(Map<String, Value> unique, String corp, int fiscalYear, String account, String raw, String fs,
            String receipt, int reportYear) {
        BigDecimal amount = KrxDailyRows.number(raw);
        if (amount == null) return;
        unique.putIfAbsent(corp + "|" + fiscalYear + "|" + account,
                new Value(corp, fiscalYear, account, amount, fs, receipt.trim(), reportYear));
    }

    /** 분기 말 잔액으로 저장하는 재무상태표 항목. */
    static final Set<String> BALANCE = Set.of("TOTAL_ASSETS", "TOTAL_LIABILITIES", "TOTAL_EQUITY", "CURRENT_ASSETS", "CURRENT_LIABILITIES");

    record Cumulative(String corpCode, int fiscalYear, String period, String account, BigDecimal amount, String receiptNo) {}

    /** 분기·반기·3분기 보고서에서 손익 항목의 1월부터 누적 값을 꺼낸다(1분기는 당기 금액이 곧 누적). */
    static List<Cumulative> cumulative(List<Map<String, String>> rows, int year, String period) {
        Set<String> hasConsolidated = new HashSet<>();
        for (Map<String, String> row : rows) if ("CFS".equals(row.get("fs_div"))) hasConsolidated.add(row.get("corp_code"));
        Map<String, Cumulative> unique = new LinkedHashMap<>();
        for (Map<String, String> row : rows) {
            String corp = row.get("corp_code");
            String fs = row.get("fs_div");
            if (corp == null || !corp.matches("[0-9]{8}") || fs == null) continue;
            if (hasConsolidated.contains(corp) ? !"CFS".equals(fs) : !"OFS".equals(fs)) continue;
            String account = ACCOUNTS.get(normalize(row.get("account_nm")));
            if (account == null) continue;
            BigDecimal amount;
            if (BALANCE.contains(account)) {
                // 재무상태표 항목은 누적이 아니라 그 분기 말 잔액이다.
                amount = KrxDailyRows.number(row.get("thstrm_amount"));
            } else if (Set.of("REVENUE", "OPERATING_INCOME", "NET_INCOME").contains(account)) {
                amount = KrxDailyRows.number(row.get("thstrm_add_amount"));
                if (amount == null && "Q1".equals(period)) amount = KrxDailyRows.number(row.get("thstrm_amount"));
            } else continue;
            String receipt = row.get("rcept_no");
            if (amount == null || receipt == null || receipt.isBlank()) continue;
            unique.putIfAbsent(corp + "|" + account, new Cumulative(corp, year, period, account, amount, receipt.trim()));
        }
        return new ArrayList<>(unique.values());
    }

    static String normalize(String name) {
        return name == null ? "" : name.replaceAll("\\s+", "");
    }
}
