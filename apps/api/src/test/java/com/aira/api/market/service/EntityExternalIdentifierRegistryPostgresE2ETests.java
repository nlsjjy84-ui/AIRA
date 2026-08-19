package com.aira.api.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aira.api.market.domain.EntityExternalIdentifier;
import com.aira.api.market.exception.ExternalIdentifierConflictException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
class EntityExternalIdentifierRegistryPostgresE2ETests {
    @Autowired private EntityExternalIdentifierRegistryService registry;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void registrationConflictNamespaceAndLookupsUseApprovedIdentitySemantics() {
        String suffix = UUID.randomUUID().toString();
        UUID companyA = UUID.randomUUID();
        UUID companyB = UUID.randomUUID();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            insertEntity(companyA, "COMPANY", "COMPANY:EXT-ID-A:" + suffix);
            insertEntity(companyB, "COMPANY", "COMPANY:EXT-ID-B:" + suffix);
            ExternalIdentifierRegistration opendart = registration(
                    companyA, "OPENDART", "CORP_CODE", "00126380");

            EntityExternalIdentifier first = registry.registerOrReuse(opendart);
            EntityExternalIdentifier repeated = registry.registerOrReuse(opendart);
            EntityExternalIdentifier otherNamespace = registry.registerOrReuse(registration(
                    companyA, "TEST_OFFICIAL", "CORP_CODE", "00126380"));
            EntityExternalIdentifier anotherIdentifier = registry.registerOrReuse(registration(
                    companyA, "OPENDART", "CORP_CODE", "00000001"));

            assertEquals(first.getId(), repeated.getId());
            assertEquals("00126380", first.getIdentifierValue());
            assertEquals(companyA, registry.findEntity(opendart.identifier())
                    .orElseThrow().getId());
            List<EntityExternalIdentifier> identifiers = registry.findIdentifiers(companyA);
            assertEquals(3, identifiers.size());
            assertEquals(1, count("""
                    SELECT count(*) FROM entity_external_identifier
                    WHERE namespace = 'OPENDART' AND identifier_type = 'CORP_CODE'
                        AND identifier_value = '00126380'
                    """));
            assertEquals(companyA, otherNamespace.getEntity().getId());
            assertEquals(companyA, anotherIdentifier.getEntity().getId());
            assertThrows(ExternalIdentifierConflictException.class,
                    () -> registry.registerOrReuse(registration(
                            companyB, "OPENDART", "CORP_CODE", "00126380")));
            status.setRollbackOnly();
        });

        assertEquals(0, count("SELECT count(*) FROM entity WHERE id IN (?, ?)",
                companyA, companyB));
    }

    @Test
    void concurrentRegistrationReturnsOneMappingWithoutDuplicates() throws Exception {
        String suffix = UUID.randomUUID().toString();
        UUID companyId = UUID.randomUUID();
        ExternalIdentifierRegistration registration = registration(
                companyId, "OPENDART", "CORP_CODE", "00999999");
        new TransactionTemplate(transactionManager).executeWithoutResult(
                status -> insertEntity(companyId, "COMPANY", "COMPANY:EXT-ID-RACE:" + suffix));

        try (var executor = Executors.newFixedThreadPool(2)) {
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            var task = (java.util.concurrent.Callable<UUID>) () -> {
                ready.countDown();
                start.await();
                return new TransactionTemplate(transactionManager).execute(
                        status -> registry.registerOrReuse(registration).getId());
            };
            Future<UUID> first = executor.submit(task);
            Future<UUID> second = executor.submit(task);
            ready.await();
            start.countDown();

            assertEquals(first.get(), second.get());
            assertEquals(1, count("""
                    SELECT count(*) FROM entity_external_identifier
                    WHERE namespace = ? AND identifier_type = ? AND identifier_value = ?
                    """, "OPENDART", "CORP_CODE", "00999999"));
        } finally {
            jdbc.update("""
                    DELETE FROM entity_external_identifier
                    WHERE entity_id = ? AND namespace = ? AND identifier_type = ?
                        AND identifier_value = ?
                    """, companyId, "OPENDART", "CORP_CODE", "00999999");
            jdbc.update("DELETE FROM entity WHERE id = ?", companyId);
        }
    }

    private ExternalIdentifierRegistration registration(
            UUID entityId, String namespace, String type, String value) {
        return new ExternalIdentifierRegistration(
                entityId, new ExternalIdentifierKey(namespace, type, value));
    }

    private void insertEntity(UUID id, String type, String canonicalKey) {
        jdbc.update("""
                INSERT INTO entity (
                    id, entity_type, canonical_name, canonical_key, active,
                    created_at, updated_at
                ) VALUES (?, ?, 'External Identifier E2E', ?, true,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, type, canonicalKey);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }
}
