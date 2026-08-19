package com.aira.api.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.UUID;
import com.aira.api.market.repository.MarketEntityRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class CompanyEntityBootstrapPostgresE2ETests {
    @Autowired private CompanyEntityBootstrapOperation operation;
    @Autowired private MarketEntityRepository entities;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void createsDistinctCompaniesMatchingDatabaseConstraintsAndRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            String suffix = UUID.randomUUID().toString();
            var first = operation.create(new CompanyEntityBootstrapCommand("E2E Company " + suffix, "KR"));
            var second = operation.create(new CompanyEntityBootstrapCommand("E2E Company " + suffix, "KR"));
            entities.flush();

            assertNotEquals(first.entityId(), second.entityId());
            assertNotEquals(first.canonicalKey(), second.canonicalKey());
            assertEquals(2, jdbc.queryForObject("""
                    SELECT count(*) FROM entity
                    WHERE id IN (?, ?) AND entity_type = 'COMPANY'
                      AND canonical_key LIKE 'COMPANY:%'
                      AND market_code IS NULL AND symbol IS NULL
                    """, Integer.class, first.entityId(), second.entityId()));
            status.setRollbackOnly();
        });
    }
}
