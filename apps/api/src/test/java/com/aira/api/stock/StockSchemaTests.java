package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StockSchemaTests {
    @Test
    void createsTablesOnceAndCanRunAgainWithoutErrors() {
        var ds = new DriverManagerDataSource("jdbc:h2:mem:stock-schema;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        var jdbc = new JdbcTemplate(ds);
        int total = StockSchema.FILES.stream().mapToInt(f -> StockSchema.statements(f).size()).sum();
        assertTrue(total >= 12);
        assertEquals(total, new StockSchema(jdbc).ensure());
        assertEquals(total, new StockSchema(jdbc).ensure(), "second run must be a no-op without errors");
        jdbc.update("INSERT INTO company_profile_cache(corp_code,payload,fetched_at,induty_code,stock_code,corp_cls) VALUES('00126380','{}',CURRENT_TIMESTAMP,'264','005930','Y')");
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM company_profile_cache WHERE induty_code='264'", Integer.class));
    }
}
