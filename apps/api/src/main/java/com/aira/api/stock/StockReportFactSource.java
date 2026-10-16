package com.aira.api.stock;

import com.aira.api.assist.ReportFactSource;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 종목 화면이 쓰는 분기 실적·사업보고서 주요정보를 사실 확인에서도 그대로 쓴다(새로 짐작해 만든 값은 없다). */
@Component
public class StockReportFactSource implements ReportFactSource {
    private final StockPageService pages;

    public StockReportFactSource(StockPageService pages) { this.pages = pages; }

    @Override
    @SuppressWarnings("unchecked")
    public Line quarterValue(UUID companyId, String item, Integer year, int quarter) {
        try {
            var company = pages.company(companyId).orElse(null);
            if (company == null) return null;
            Line found = null;
            for (var row : (List<Map<String, Object>>) pages.quarters(company).get("items")) {
                int y = ((Number) row.get("year")).intValue();
                int q = ((Number) row.get("quarter")).intValue();
                if (q != quarter || (year != null && y != year)) continue;
                Object value = ((Map<String, Object>) row.get("values")).get(item);
                if (!(value instanceof BigDecimal amount)) continue;
                String note = Boolean.TRUE.equals(row.get("derived"))
                        ? " 분기 값은 공시된 누적 실적에서 앞 분기까지의 누적을 빼서 계산했습니다." : "";
                if (found == null || y > found.year()) found = new Line(y, q, amount, (String) row.get("receiptNo"), note);
            }
            return found;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public Line dividendPerShare(UUID companyId, Integer year) {
        var details = details(companyId, year);
        if (details == null) return null;
        for (var row : rows(details, "dividends")) {
            String se = row.getOrDefault("se", "");
            String kind = row.getOrDefault("stock_knd", "");
            if (!se.replace(" ", "").contains("주당현금배당금")) continue;
            if (!(kind == null || kind.isBlank() || kind.equals("-") || kind.contains("보통"))) continue;
            BigDecimal amount = KrxDailyRows.number(row.get("thstrm"));
            if (amount == null) continue;
            return new Line(((Number) details.get("year")).intValue(), null, amount, (String) details.get("receiptNo"),
                    " 사업보고서 '배당에 관한 사항'의 보통주 주당 현금배당금입니다.");
        }
        return null;
    }

    @Override
    public Line employees(UUID companyId, Integer year) {
        var details = details(companyId, year);
        if (details == null) return null;
        List<Map<String, String>> rows = rows(details, "employees");
        if (rows.isEmpty()) return null;
        List<Map<String, String>> use = rows.stream().filter(r -> isTotal(r.get("fo_bbm"))).toList();
        if (use.isEmpty()) use = rows;
        List<Map<String, String>> bothSexes = use.stream().filter(r -> isTotal(r.get("sexdstn"))).toList();
        if (!bothSexes.isEmpty()) use = bothSexes;
        BigDecimal sum = BigDecimal.ZERO;
        boolean any = false;
        for (var row : use) {
            BigDecimal n = KrxDailyRows.number(row.get("sm"));
            if (n != null) { sum = sum.add(n); any = true; }
        }
        if (!any) return null;
        return new Line(((Number) details.get("year")).intValue(), null, sum, (String) details.get("receiptNo"),
                " 사업보고서 '직원 현황'의 합계(정규직·기간제 포함)입니다.");
    }

    private static boolean isTotal(String value) {
        return value != null && (value.contains("합계") || value.contains("총계") || value.contains("전체"));
    }

    private Map<String, Object> details(UUID companyId, Integer year) {
        try {
            var company = pages.company(companyId).orElse(null);
            if (company == null) return null;
            Map<String, Object> details = pages.reportDetails(company);
            if (!"AVAILABLE".equals(details.get("state"))) return null;
            if (year != null && ((Number) details.get("year")).intValue() != year) return null;
            return details;
        } catch (RuntimeException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, String>> rows(Map<String, Object> details, String key) {
        Object value = details.get(key);
        return value instanceof List<?> list ? (List<Map<String, String>>) list : List.of();
    }
}
