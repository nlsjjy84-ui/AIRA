package com.aira.api.personalfinance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aira.api.personalfinance.service.PersonalFinanceDataDeletionService;
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
class PersonalFinanceDataDeletionPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PersonalFinanceDataDeletionService deletion;

    @Test
    void explicitEraseRemovesOnlyOwnersFinanceDataAndLeavesAiraAccount() {
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        try {
            seedFinanceGraph(owner, "01");
            seedFinanceGraph(other, "02");
            deletion.deleteAll(owner);

            assertEquals(0, count("monthly_budget", owner));
            assertEquals(0, count("personal_finance_transaction", owner));
            assertEquals(0, count("personal_finance_account", owner));
            assertEquals(0, count("personal_finance_connection", owner));
            assertEquals(0, count("personal_finance_consent", owner));
            assertEquals(0, count("personal_finance_access_grant", owner));
            assertEquals(0, count("personal_finance_reauth_guard", owner));
            assertEquals(0, countAiExecutions(owner));
            assertEquals(1, count("app_user", owner));

            assertEquals(1, count("personal_finance_consent", other));
            assertEquals(1, count("personal_finance_connection", other));
            assertEquals(1, count("personal_finance_account", other));
            assertEquals(1, count("personal_finance_transaction", other));
            assertEquals(1, count("monthly_budget", other));
            assertEquals(1, count("personal_finance_access_grant", other));
            assertEquals(1, count("personal_finance_reauth_guard", other));
            assertEquals(1, countAiExecutions(other));
        } finally {
            jdbc.update("DELETE FROM app_user WHERE id IN (?,?)", owner, other);
        }
    }

    private int countAiExecutions(UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM ai_execution WHERE personal_finance_user_id=?", Integer.class, userId);
    }

    private int count(String table, UUID userId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE "
                + (table.equals("app_user") ? "id" : "user_id") + "=?", Integer.class, userId);
    }
    private void seedFinanceGraph(UUID userId, String hex) {
        UUID credential = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        UUID consent = UUID.randomUUID();
        UUID connection = UUID.randomUUID();
        UUID account = UUID.randomUUID();
        String nickname = "Erase" + userId.toString().replace("-", "").substring(0, 8);

        jdbc.update("INSERT INTO app_user(id,nickname,nickname_normalized,status) VALUES(?,?,?,'ACTIVE')",
                userId, nickname, nickname.toLowerCase());
        jdbc.update("""
                INSERT INTO authentication_credential(id,user_id,password_hash,password_changed_at,status)
                VALUES (?,?, '$argon2id$fixture', CURRENT_TIMESTAMP, 'ACTIVE')
                """, credential, userId);
        jdbc.update("""
                INSERT INTO user_session(
                    id,user_id,credential_id,token_hash,issued_at,last_seen_at,
                    idle_expires_at,absolute_expires_at)
                VALUES (?,?,?,decode(?,'hex'),CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP + interval '30 minutes',CURRENT_TIMESTAMP + interval '12 hours')
                """, session, userId, credential, hex);
        jdbc.update("""
                INSERT INTO personal_finance_consent(
                    id,user_id,source_type,provider_key,policy_version,
                    allow_accounts,allow_transactions,consented_at)
                VALUES (?,?,'DEMO_IMPORT',?,'v1',true,true,CURRENT_TIMESTAMP)
                """, consent, userId, "provider-" + hex);
        jdbc.update("""
                INSERT INTO personal_finance_connection(
                    id,user_id,source_type,provider_key,display_name,status,
                    consented_at,created_at,updated_at,consent_id)
                VALUES (?,?,'DEMO_IMPORT',?,'Demo','ACTIVE',
                        CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?)
                """, connection, userId, "provider-" + hex, consent);
        jdbc.update("""
                INSERT INTO personal_finance_account(
                    id,user_id,connection_id,account_type,display_name,currency_code,
                    source_ref_hash,created_at,updated_at)
                VALUES (?,?,?,'CHECKING','Demo account','KRW',decode(?,'hex'),
                        CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, account, userId, connection, hex + hex);
        jdbc.update("""
                INSERT INTO personal_finance_transaction(
                    id,user_id,account_id,occurred_at,direction,amount,currency_code,
                    merchant_name,category,source_ref_hash)
                VALUES (?,?,?,CURRENT_TIMESTAMP,'EXPENSE',1000,'KRW','Demo merchant',
                        'FOOD',decode(?,'hex'))
                """, UUID.randomUUID(), userId, account, hex + hex + hex);
        jdbc.update("""
                INSERT INTO monthly_budget(
                    id,user_id,budget_month,category,amount,currency_code)
                VALUES (?,?,date_trunc('month',CURRENT_DATE)::date,'TOTAL',500000,'KRW')
                """, UUID.randomUUID(), userId);
        jdbc.update("""
                INSERT INTO personal_finance_access_grant(
                    id,user_id,session_id,token_hash,issued_at,expires_at)
                VALUES (?,?,?,decode(?,'hex'),CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP + interval '10 minutes')
                """, UUID.randomUUID(), userId, session, hex + "ff");
        jdbc.update("""
                INSERT INTO ai_execution(
                    id,provider_key,model_key,task_type,prompt_version,personal_finance_user_id,
                    input_fingerprint,cache_key,cache_hit,status,started_at,completed_at)
                VALUES (? ,'TEST','test-model','PERSONAL_FINANCE_EXPLANATION','test-v1',?,
                        decode(? ,'hex'),decode(? ,'hex'),false,'SUCCEEDED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, UUID.randomUUID(), userId, hex + "aa", hex + "bb");
        jdbc.update("""
                INSERT INTO personal_finance_reauth_guard(user_id,failed_attempts,updated_at)
                VALUES (?,2,CURRENT_TIMESTAMP)
                """, userId);
    }
}
