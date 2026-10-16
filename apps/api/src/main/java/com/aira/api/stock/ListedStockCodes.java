package com.aira.api.stock;

import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 가장 최근 거래일에 거래소 시세가 있는 종목코드(지금 상장된 종목). OpenDART 회사 목록에는 상장폐지된 회사의
 * 종목코드도 남아 있어서, 전체 수집이 그런 회사에 헛호출하지 않도록 거른다. 자료가 없으면 빈 집합(거르지 않음).
 */
@Component
public class ListedStockCodes {
    private final JdbcTemplate jdbc;

    public ListedStockCodes(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Set<String> current() {
        try {
            return new HashSet<>(jdbc.query("""
                    SELECT short_code FROM stock_price_daily
                    WHERE trading_date = (SELECT max(trading_date) FROM stock_price_daily WHERE market IN ('KOSPI', 'KOSDAQ'))
                      AND market IN ('KOSPI', 'KOSDAQ')
                    """, (rs, n) -> rs.getString(1).trim()));
        } catch (RuntimeException e) {
            return Set.of();
        }
    }
}
