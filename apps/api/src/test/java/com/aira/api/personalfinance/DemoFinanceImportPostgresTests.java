package com.aira.api.personalfinance;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.personalfinance.domain.FinanceConnectionSource;
import com.aira.api.personalfinance.dto.FinanceConsentRequest;
import com.aira.api.personalfinance.exception.FinanceConsentRequiredException;
import com.aira.api.personalfinance.service.DemoFinanceImportService;
import com.aira.api.personalfinance.service.FinanceConsentService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class DemoFinanceImportPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoFinanceImportService imports;
    @Autowired FinanceConsentService consents;

    @Test
    void requiresExplicitFullDemoConsent() {
        UUID user = createUser("NoConsent", "Asia/Seoul");
        try {
            assertThrows(FinanceConsentRequiredException.class, () -> imports.importDemo(user));

            consents.create(user, new FinanceConsentRequest(
                    FinanceConnectionSource.DEMO_IMPORT, DemoFinanceImportService.PROVIDER_KEY,
                    "v1", true, false));
            assertThrows(FinanceConsentRequiredException.class, () -> imports.importDemo(user));
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id=?", user);
        }
    }

    @Test
    void myDataConsentCannotAuthorizeDemoImport() {
        UUID user = createUser("MyDataOnly", "Asia/Seoul");
        try {
            consents.create(user, new FinanceConsentRequest(
                    FinanceConnectionSource.MYDATA_API, "future-mydata-provider",
                    "v1", true, true));
            assertThrows(FinanceConsentRequiredException.class, () -> imports.importDemo(user));
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id=?", user);
        }
    }

    @Test
    void repeatedImportIsIdempotentAndOwnedPerUser() {
        UUID first = createUser("DemoFirst", "Asia/Seoul");
        UUID second = createUser("DemoSecond", "Asia/Seoul");
        try {
            createFullDemoConsent(first);
            createFullDemoConsent(second);

            var firstRun = imports.importDemo(first);
            assertTrue(firstRun.demoData());
            assertEquals(DemoFinanceImportService.PROVIDER_KEY, firstRun.providerKey());
            assertEquals(2, firstRun.accountCount());
            assertEquals(15, firstRun.insertedTransactionCount());
            assertEquals(2, count("personal_finance_account", first));
            assertEquals(15, count("personal_finance_transaction", first));
            assertNotNull(jdbc.queryForObject(
                    "SELECT last_synced_at FROM personal_finance_connection WHERE id=?",
                    java.time.OffsetDateTime.class, firstRun.connectionId()));

            var secondRun = imports.importDemo(first);
            assertEquals(firstRun.connectionId(), secondRun.connectionId());
            assertEquals(0, secondRun.insertedTransactionCount());
            assertEquals(2, count("personal_finance_account", first));
            assertEquals(15, count("personal_finance_transaction", first));

            imports.importDemo(second);
            assertEquals(15, count("personal_finance_transaction", second));
            assertNotEquals(firstRun.connectionId(), connectionId(second));
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id IN (?,?)", first, second);
        }
    }

    private void createFullDemoConsent(UUID userId) {
        consents.create(userId, new FinanceConsentRequest(
                FinanceConnectionSource.DEMO_IMPORT, DemoFinanceImportService.PROVIDER_KEY,
                "v1", true, true));
    }

    private UUID createUser(String prefix, String timezone) {
        UUID id = UUID.randomUUID();
        String nickname = prefix + id.toString().replace("-", "").substring(0, 8);
        jdbc.update("""
                INSERT INTO app_user(id,nickname,nickname_normalized,status,timezone)
                VALUES (?,?,?,'ACTIVE',?)
                """, id, nickname, nickname.toLowerCase(), timezone);
        return id;
    }

    private int count(String table, UUID userId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE user_id=?",
                Integer.class, userId);
    }

    private UUID connectionId(UUID userId) {
        return jdbc.queryForObject("""
                SELECT id FROM personal_finance_connection
                WHERE user_id=? AND provider_key=?
                """, UUID.class, userId, DemoFinanceImportService.PROVIDER_KEY);
    }
}
