package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CashFlowRowsTests {
    @Test
    void picksCashFlowLinesByIdThenNameAndComputesFreeCashFlow() {
        var rows = List.of(
                Map.of("sj_div", "CF", "account_id", "ifrs-full_CashFlowsFromUsedInOperatingActivities", "account_nm", "영업활동 현금흐름", "thstrm_amount", "100", "frmtrm_amount", "80", "bfefrmtrm_amount", "60"),
                Map.of("sj_div", "CF", "account_id", "-표준계정코드 미사용-", "account_nm", "투자활동으로 인한 현금흐름", "thstrm_amount", "-50", "frmtrm_amount", "-40"),
                Map.of("sj_div", "CF", "account_id", "ifrs-full_PurchaseOfPropertyPlantAndEquipmentClassifiedAsInvestingActivities", "account_nm", "유형자산의 취득", "thstrm_amount", "-30", "frmtrm_amount", "-20"),
                Map.of("sj_div", "IS", "account_id", "x", "account_nm", "영업활동 비슷한 손익", "thstrm_amount", "999"));
        var out = CashFlowRows.convert(rows, 2025);
        assertEquals(List.of(2023, 2024, 2025), out.stream().map(m -> m.get("year")).toList());
        var latest = (Map<?, ?>) out.get(2).get("values");
        assertEquals(new BigDecimal("100"), latest.get("OPERATING"));
        assertEquals(new BigDecimal("-50"), latest.get("INVESTING"));
        assertEquals(new BigDecimal("70"), latest.get("FREE_CASH_FLOW"));
        assertTrue(((Map<?, ?>) out.get(0).get("values")).get("FREE_CASH_FLOW") == null);
        assertTrue(CashFlowRows.convert(List.of(), 2025).isEmpty());
    }
}
