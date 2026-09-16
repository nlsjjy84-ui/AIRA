package com.aira.api.market.query;

import static org.junit.jupiter.api.Assertions.*;
import com.aira.api.market.dto.CanonicalDataState;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {"spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class CanonicalEntitySearchPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired CanonicalEntitySearchQuery search;

    @Test void sameDisplayNameReturnsDistinctTypedCanonicalIdentities() {
        UUID company = UUID.randomUUID(), security = UUID.randomUUID();
        String name = "Search" + company.toString().replace("-", "").substring(0, 12);
        String corpCode = company.toString().replace("-", "").substring(0, 8);
        try {
            jdbc.update("INSERT INTO entity(id,entity_type,canonical_name,canonical_key,active) VALUES(?,'COMPANY',?,?,true)",
                    company, name, "COMPANY:" + company);
            jdbc.update("""
                    INSERT INTO entity(id,entity_type,canonical_name,canonical_key,market_code,symbol,active)
                    VALUES(?,'SECURITY',?,?,'KOSPI','123456',true)
                    """, security, name, "SECURITY:" + security);
            jdbc.update("INSERT INTO entity_external_identifier(entity_id,namespace,identifier_type,identifier_value) VALUES(?,'OPENDART','CORP_CODE',?)", company, corpCode);
            var result = search.find(name);
            assertEquals(CanonicalDataState.AVAILABLE, result.state());
            assertEquals(2, result.entities().size());
            assertEquals(java.util.Set.of(company, security), result.entities().stream()
                    .map(item -> item.entityId()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(java.util.Set.of("COMPANY", "SECURITY"), result.entities().stream()
                    .map(item -> item.entityType().name()).collect(java.util.stream.Collectors.toSet()));
            assertEquals(corpCode, result.entities().stream().filter(item -> item.entityId().equals(company)).findFirst().orElseThrow().externalIdentifier());
            assertEquals(CanonicalDataState.NO_DATA, search.find(name + "missing").state());
            assertEquals(CanonicalDataState.UNSUPPORTED, search.find(" ").state());
        } finally {
            jdbc.update("DELETE FROM entity_external_identifier WHERE entity_id=?", company);
            jdbc.update("DELETE FROM entity WHERE id IN (?,?)", company, security);
        }
    }
}
