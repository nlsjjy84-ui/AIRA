package com.aira.api.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@TestPropertySource(properties = "aira.auth.session.cookie-secure=false")
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIRA_AUTH_E2E_USER_A", matches = ".+")
@EnabledIfEnvironmentVariable(named = "AIRA_AUTH_E2E_USER_B", matches = ".+")
class AuthenticatedInterestBrowserCleanupPostgresTests {
    private static final String SAMSUNG_ID = "5eafc0b5-c163-4cea-8dbd-131265004e95";

    @Autowired JdbcTemplate jdbc;

    @Test
    void deletesOnlyTheTwoExplicitBrowserUsersAndPreservesSamsungFacts() {
        String userA = required("AIRA_AUTH_E2E_USER_A");
        String userB = required("AIRA_AUTH_E2E_USER_B");
        if (userA.equals(userB)) throw new IllegalStateException("Browser users must be distinct");

        jdbc.update("DELETE FROM app_user WHERE nickname IN (?, ?)", userA, userB);
        assertEquals(0, jdbc.queryForObject(
                "SELECT count(*) FROM app_user WHERE nickname IN (?, ?)", Integer.class, userA, userB));
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM entity WHERE id = ?::uuid AND entity_type = 'COMPANY'",
                Integer.class, SAMSUNG_ID));
        List<BigDecimal> values = jdbc.queryForList("""
                SELECT value_number FROM fact
                WHERE subject_entity_id = ?::uuid
                  AND period_start = DATE '2025-01-01'
                  AND period_end = DATE '2025-12-31'
                  AND predicate IN ('REVENUE', 'OPERATING_INCOME')
                ORDER BY predicate
                """, BigDecimal.class, SAMSUNG_ID);
        assertEquals(List.of(new BigDecimal("43601051000000"),
                new BigDecimal("333605938000000")), values);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required");
        return value;
    }
}
