package com.aira.api.stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** KRX 일별 매매정보 응답 한 줄을 그래프용 행으로 바꾼다. 숫자가 아니거나 비어 있으면 그 칸만 비운다. */
final class KrxDailyRows {
    private KrxDailyRows() {}

    static List<StockPriceStore.Row> convert(String market, LocalDate date, List<Map<String, String>> rows) {
        List<StockPriceStore.Row> out = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String code = row.get("ISU_CD");
            if (code == null || !code.trim().matches("[0-9A-Z]{6}")) continue;
            BigDecimal close = number(row.get("TDD_CLSPRC"));
            if (close == null || close.signum() <= 0) continue;
            out.add(new StockPriceStore.Row(code.trim(), date, market, close, number(row.get("TDD_OPNPRC")),
                    number(row.get("TDD_HGPRC")), number(row.get("TDD_LWPRC")), number(row.get("FLUC_RT")),
                    longValue(row.get("ACC_TRDVOL")), number(row.get("ACC_TRDVAL")), number(row.get("MKTCAP")),
                    longValue(row.get("LIST_SHRS"))));
        }
        return out;
    }

    static List<StockPriceStore.Row> convertIndex(String market, String store, LocalDate date, List<Map<String, String>> rows) {
        java.util.Set<String> names = "KOSPI".equals(market) ? java.util.Set.of("KOSPI", "코스피") : java.util.Set.of("KOSDAQ", "코스닥");
        List<StockPriceStore.Row> out = new ArrayList<>();
        for (Map<String, String> row : rows) {
            String name = row.get("IDX_NM");
            if (name == null || !names.contains(name.trim())) continue;
            BigDecimal close = number(row.get("CLSPRC_IDX"));
            if (close == null) continue;
            out.add(new StockPriceStore.Row(market, date, store, close, number(row.get("OPNPRC_IDX")), number(row.get("HGPRC_IDX")),
                    number(row.get("LWPRC_IDX")), number(row.get("FLUC_RT")), null, number(row.get("ACC_TRDVAL")),
                    number(row.get("MKTCAP")), null));
            break;
        }
        return out;
    }

    static BigDecimal number(String raw) {
        if (raw == null) return null;
        String token = raw.trim().replace(",", "");
        if (token.isEmpty() || token.equals("-") || !token.matches("[+-]?[0-9]+(\\.[0-9]+)?")) return null;
        return new BigDecimal(token);
    }

    private static Long longValue(String raw) {
        BigDecimal value = number(raw);
        if (value == null) return null;
        try { return value.longValueExact(); } catch (ArithmeticException e) { return null; }
    }
}
