package com.aira.api.stock;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 종목 화면용 요약 표를 서버가 켜질 때 없으면 만든다(있으면 그대로 둔다).
 * 운영에서 Flyway를 끈 채 배포해도 화면이 동작하게 하려는 것이며, 같은 SQL 파일(V25~V27, 모두 IF NOT EXISTS)을 쓰므로
 * 나중에 Flyway를 켜도 충돌하지 않는다. 실패해도 서버 시작을 막지 않는다.
 */
@Component
@Order(0)
public class StockSchema implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StockSchema.class);
    static final List<String> FILES = List.of("V25__create_stock_page_cache.sql", "V26__add_stock_profile_industry.sql", "V27__create_stock_quarterly.sql", "V28__create_user_stock_note.sql", "V29__create_user_price_alert.sql", "V30__create_user_study.sql", "V31__create_study_class.sql",
            "V32__allow_extended_company_financial_facts.sql", "V33__create_plan_payment.sql");
    private final JdbcTemplate jdbc;

    public StockSchema(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void run(ApplicationArguments args) { ensure(); }

    /** 운영 DB(PostgreSQL)에서만 실행하는 파일. 테스트용 H2는 Hibernate가 만든 표라 제약 조건을 바꾸지 않는다. */
    static final java.util.Set<String> POSTGRES_ONLY = java.util.Set.of("V32__allow_extended_company_financial_facts.sql");

    private boolean postgres() {
        try {
            String name = jdbc.execute((org.springframework.jdbc.core.ConnectionCallback<String>)
                    connection -> connection.getMetaData().getDatabaseProductName());
            return name != null && name.toLowerCase(java.util.Locale.ROOT).contains("postgres");
        } catch (RuntimeException e) {
            return false;
        }
    }

    public int ensure() {
        int applied = 0;
        boolean postgres = postgres();
        for (String file : FILES) {
            if (!postgres && POSTGRES_ONLY.contains(file)) continue;
            for (String statement : statements(file)) {
                try { jdbc.execute(statement); applied++; }
                catch (RuntimeException e) { log.warn("Stock schema statement skipped ({}): {}", file, e.getMessage()); }
            }
        }
        log.info("Stock page schema checked: {} statements", applied);
        return applied;
    }

    static List<String> statements(String file) {
        try {
            String sql = new ClassPathResource("db/migration/" + file).getContentAsString(StandardCharsets.UTF_8);
            return java.util.Arrays.stream(sql.replaceAll("(?m)^\\s*--.*$", "").split(";"))
                    .map(String::trim).filter(s -> !s.isEmpty()).toList();
        } catch (IOException e) {
            return List.of();
        }
    }
}
