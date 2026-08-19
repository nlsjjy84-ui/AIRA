package com.aira.api.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aira.api.market.domain.Source;
import com.aira.api.market.domain.SourceType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@Transactional
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class SourceRegistryPostgresE2ETests {
    @Autowired private SourceRegistryService registry;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void registrationIsIdempotentAndEvidenceKeepsTheSourceForeignKey() {
        String externalKey = "source-registry-e2e-" + UUID.randomUUID();
        SourceRegistration registration = new SourceRegistration(
                SourceType.REGULATOR, externalKey, "Source Registry E2E", "example.gov");

        Source first = registry.registerOrReuse(registration);
        Source repeated = registry.registerOrReuse(registration);

        assertEquals(first.getId(), repeated.getId());
        assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM source WHERE source_type = ? AND external_key = ?",
                Integer.class, SourceType.REGULATOR.name(), externalKey));

        UUID evidenceId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO evidence (
                    id, source_id, evidence_type, original_url, content_hash,
                    collected_at, revision, status
                ) VALUES (?, ?, 'DISCLOSURE', ?, ?, CURRENT_TIMESTAMP, 1, 'ACTIVE')
                """, evidenceId, first.getId(), "https://example.gov/" + externalKey,
                new byte[] {1, 2, 3});

        assertEquals(first.getId(), jdbc.queryForObject(
                "SELECT source_id FROM evidence WHERE id = ?", UUID.class, evidenceId));
    }
}
