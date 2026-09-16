package com.aira.api.personalfinance;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.personalfinance.ai.AiExplanationInput;
import com.aira.api.personalfinance.ai.AiProviderException;
import com.aira.api.personalfinance.ai.AiProviderResult;
import com.aira.api.personalfinance.ai.PersonalFinanceAiProvider;
import com.aira.api.personalfinance.service.PersonalFinanceAiExplanationService;
import com.aira.api.personalfinance.service.PersonalFinancePatternService;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true", "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class PersonalFinanceAiPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PersonalFinancePatternService patterns;
    @Autowired ObjectMapper json;
    @Autowired Clock clock;

    @Test
    void successfulProviderCallIsRecordedAndOnlyAggregateInputReachesProvider() {
        UUID owner = createUser("AiSuccess");
        UUID account = createAccount(owner, "41");
        String providerKey = "TEST_AI_SUCCESS_" + owner.toString().substring(0, 8);
        try {
            insertExpense(owner, account, "2026-08-10T03:00:00Z", "FOOD", "10000", "KRW", "42");
            insertExpense(owner, account, "2026-09-10T03:00:00Z", "FOOD", "15000", "KRW", "43");
            CapturingProvider provider = new CapturingProvider(providerKey, false);
            var service = new PersonalFinanceAiExplanationService(patterns, provider, jdbc, json, clock);

            var result = service.explain(owner, "2026-09", "KRW");
            assertEquals("AI", result.method());
            assertEquals("집계 기반 AI 설명", result.explanation());
            assertNotNull(result.aiExecutionId());
            assertNotNull(provider.input);
            assertEquals("2026-09", provider.input.month());
            assertEquals(new BigDecimal("15000.00"), provider.input.totalCurrent());
            assertEquals(1, provider.input.categories().size());

            var row = jdbc.queryForMap("SELECT * FROM ai_execution WHERE id=?", result.aiExecutionId());
            assertEquals(providerKey, row.get("provider_key"));
            assertEquals("test-model", row.get("model_key"));
            assertEquals("PERSONAL_FINANCE_EXPLANATION", row.get("task_type"));
            assertEquals(owner, row.get("personal_finance_user_id"));
            assertNull(row.get("event_id"));
            assertNull(row.get("assessment_id"));
            assertEquals("SUCCEEDED", row.get("status"));
            assertEquals(11L, ((Number) row.get("input_tokens")).longValue());
            assertEquals(7L, ((Number) row.get("output_tokens")).longValue());
        } finally {
            jdbc.update("DELETE FROM ai_execution WHERE provider_key=?", providerKey);
            jdbc.update("DELETE FROM app_user WHERE id=?", owner);
        }
    }

    @Test
    void providerFailureIsRecordedAndFallsBackToRuleWithoutPretendingAiSucceeded() {
        UUID owner = createUser("AiFailure");
        UUID account = createAccount(owner, "51");
        String providerKey = "TEST_AI_FAIL_" + owner.toString().substring(0, 8);
        try {
            insertExpense(owner, account, "2026-08-10T03:00:00Z", "FOOD", "10000", "KRW", "52");
            insertExpense(owner, account, "2026-09-10T03:00:00Z", "FOOD", "12000", "KRW", "53");
            CapturingProvider provider = new CapturingProvider(providerKey, true);
            var service = new PersonalFinanceAiExplanationService(patterns, provider, jdbc, json, clock);

            var result = service.explain(owner, "2026-09", "KRW");
            assertEquals("RULE", result.method());
            assertNull(result.explanation());
            assertNull(result.aiExecutionId());
            assertEquals("RULE", result.pattern().method());

            var row = jdbc.queryForMap("""
                    SELECT * FROM ai_execution
                    WHERE provider_key=? ORDER BY started_at DESC LIMIT 1
                    """, providerKey);
            assertEquals("FAILED", row.get("status"));
            assertEquals("TEST_FAILURE", row.get("error_code"));
            assertNotNull(row.get("completed_at"));
        } finally {
            jdbc.update("DELETE FROM ai_execution WHERE provider_key=?", providerKey);
            jdbc.update("DELETE FROM app_user WHERE id=?", owner);
        }
    }

    @Test
    void databaseRejectsPersonalFinanceAiExecutionWithoutUserOwnership() {
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("""
                INSERT INTO ai_execution(
                    id,provider_key,model_key,task_type,prompt_version,
                    input_fingerprint,cache_key,cache_hit,status,started_at)
                VALUES (?,'TEST','test-model','PERSONAL_FINANCE_EXPLANATION','test-v1',
                        decode('aa','hex'),decode('bb','hex'),false,'RUNNING',CURRENT_TIMESTAMP)
                """, UUID.randomUUID()));
    }

    private UUID createUser(String prefix) {
        UUID id = UUID.randomUUID();
        String nickname = prefix + id.toString().replace("-", "").substring(0, 8);
        jdbc.update("""
                INSERT INTO app_user(id,nickname,nickname_normalized,status,timezone)
                VALUES (?,?,?,'ACTIVE','Asia/Seoul')
                """, id, nickname, nickname.toLowerCase());
        return id;
    }

    private UUID createAccount(UUID userId, String hex) {
        UUID connection = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO personal_finance_connection(
                    id,user_id,source_type,provider_key,display_name,status,consented_at)
                VALUES (?,?,'DEMO_IMPORT',?,'AI test','ACTIVE',CURRENT_TIMESTAMP)
                """, connection, userId, "ai-test-" + hex);
        jdbc.update("""
                INSERT INTO personal_finance_account(
                    id,user_id,connection_id,account_type,display_name,currency_code,source_ref_hash)
                VALUES (?,?,?,'CHECKING','AI test account','KRW',decode(?,'hex'))
                """, account, userId, connection, hex + hex);
        return account;
    }

    private void insertExpense(UUID userId, UUID accountId, String occurredAt,
            String category, String amount, String currency, String hex) {
        jdbc.update("""
                INSERT INTO personal_finance_transaction(
                    id,user_id,account_id,occurred_at,direction,amount,currency_code,
                    merchant_name,category,source_ref_hash)
                VALUES (?,?,?,?,'EXPENSE',?,?, 'AI test merchant',?,decode(?,'hex'))
                """, UUID.randomUUID(), userId, accountId, OffsetDateTime.parse(occurredAt),
                new BigDecimal(amount), currency, category, hex + hex + hex);
    }

    private static final class CapturingProvider implements PersonalFinanceAiProvider {
        private final String providerKey;
        private final boolean fail;
        private AiExplanationInput input;

        CapturingProvider(String providerKey, boolean fail) {
            this.providerKey = providerKey;
            this.fail = fail;
        }
        public boolean available() { return true; }
        public String providerKey() { return providerKey; }
        public String modelKey() { return "test-model"; }
        public AiProviderResult explain(AiExplanationInput input) {
            this.input = input;
            if (fail) throw new AiProviderException("TEST_FAILURE");
            return new AiProviderResult("집계 기반 AI 설명", 11, 7);
        }
    }
}
