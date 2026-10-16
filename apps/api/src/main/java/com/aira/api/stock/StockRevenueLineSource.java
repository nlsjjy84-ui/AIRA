package com.aira.api.stock;

import com.aira.api.assist.RevenueLineSource;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** 종목 화면이 이미 받아 두는 OpenDART 전체 재무제표(24시간 보관)에서 금융회사의 수익 줄을 찾는다. */
@Component
public class StockRevenueLineSource implements RevenueLineSource {
    private final StockPageService pages;

    public StockRevenueLineSource(StockPageService pages) { this.pages = pages; }

    @Override
    public Line revenueLine(UUID companyId, Integer year) {
        try {
            return pages.financialRevenueLine(companyId, year);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
