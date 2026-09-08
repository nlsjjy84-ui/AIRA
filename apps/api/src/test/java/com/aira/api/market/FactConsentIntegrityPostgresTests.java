package com.aira.api.market;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.user.exception.InterestEntityNotFoundException;
import com.aira.api.user.service.UserInterestService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "aira.auth.recovery-email.encryption-key=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",
        "aira.auth.recovery-email.lookup-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
})
@EnabledIfEnvironmentVariable(named = "DB_PASSWORD", matches = ".+")
class FactConsentIntegrityPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @Autowired UserInterestService interests;

    @Test
    void supportedFactAssertionInvariantIsDeferredAndCoversOrphaningOperations() {
        Fixture f = createFixture();
        try {
            UUID orphan = UUID.randomUUID();
            assertThrows(RuntimeException.class, () -> tx(() -> insertFact(orphan, f, "SUPPORTED")));
            assertEquals(0, count("SELECT count(*) FROM fact WHERE id=?", orphan));

            UUID supported = UUID.randomUUID();
            tx(() -> { insertFact(supported, f, "SUPPORTED"); insertAssertion(supported, f.evidence()); });
            assertEquals(1, count("SELECT count(*) FROM fact_assertion WHERE fact_id=?", supported));

            assertThrows(RuntimeException.class,
                    () -> tx(() -> jdbc.update("DELETE FROM fact_assertion WHERE fact_id=?", supported)));
            assertEquals(1, count("SELECT count(*) FROM fact_assertion WHERE fact_id=?", supported));

            UUID conflicting = UUID.randomUUID();
            tx(() -> insertFact(conflicting, f, "CONFLICTING"));
            assertThrows(RuntimeException.class, () -> tx(() -> jdbc.update(
                    "UPDATE fact_assertion SET fact_id=? WHERE fact_id=?", conflicting, supported)));
            assertEquals(1, count("SELECT count(*) FROM fact_assertion WHERE fact_id=?", supported));

            UUID failedAssertion = UUID.randomUUID();
            assertThrows(RuntimeException.class, () -> tx(() -> {
                insertFact(failedAssertion, f, "SUPPORTED");
                jdbc.update("""
                        INSERT INTO fact_assertion(fact_id,evidence_id,locator,value_type,value_number)
                        VALUES(?,?,'','NUMBER',100)
                        """, failedAssertion, f.evidence());
            }));
            assertEquals(0, count("SELECT count(*) FROM fact WHERE id=?", failedAssertion));

            tx(() -> {
                jdbc.update("DELETE FROM fact_assertion WHERE fact_id=?", supported);
                jdbc.update("DELETE FROM fact WHERE id=?", supported);
            });
            assertEquals(0, count("SELECT count(*) FROM fact WHERE id=?", supported));
        } finally { cleanup(f); }
    }

    @Test
    void databaseAndJpaInterestCreationDefaultToNoConsentAndOwnershipIsScoped() {
        Fixture f = createFixture();
        UUID userA = createUser("ConsentA");
        UUID userB = createUser("ConsentB");
        UUID entityB = createEntity();
        try {
            UUID raw = UUID.randomUUID();
            jdbc.update("""
                    INSERT INTO user_interest(id,user_id,entity_id,created_at,updated_at)
                    VALUES(?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                    """, raw, userA, f.entity());
            assertFalse(jdbc.queryForObject(
                    "SELECT alert_enabled FROM user_interest WHERE id=?", Boolean.class, raw));

            assertFalse(interests.add(userB, entityB).alertEnabled());
            assertTrue(interests.setAlertEnabled(userB, entityB, true).alertEnabled());
            assertFalse(interests.setAlertEnabled(userB, entityB, false).alertEnabled());
            assertThrows(InterestEntityNotFoundException.class,
                    () -> interests.setAlertEnabled(userA, entityB, true));
        } finally {
            jdbc.update("DELETE FROM user_interest WHERE user_id IN (?,?)", userA, userB);
            jdbc.update("DELETE FROM entity WHERE id=?", entityB);
            jdbc.update("DELETE FROM app_user WHERE id IN (?,?)", userA, userB);
            cleanup(f);
        }
    }

    @Test
    void migrationsExposeDeferredTriggersAndFalseConsentDefault() {
        assertEquals(2, count("""
                SELECT count(*) FROM pg_trigger
                WHERE tgname IN ('supported_fact_requires_assertion','supported_fact_retains_assertion')
                  AND tgdeferrable AND tginitdeferred
                """));
        assertEquals("false", jdbc.queryForObject("""
                SELECT column_default FROM information_schema.columns
                WHERE table_schema='public' AND table_name='user_interest'
                  AND column_name='alert_enabled'
                """, String.class));
    }

    private Fixture createFixture() {
        UUID source=UUID.randomUUID(), entity=createEntity(), event=UUID.randomUUID(), evidence=UUID.randomUUID();
        jdbc.update("INSERT INTO source(id,source_type,name) VALUES(?,'REGULATOR','Fact consent test')", source);
        jdbc.update("""
                INSERT INTO event(id,event_type,title,first_observed_at,last_observed_at,status,created_at,updated_at)
                VALUES(?,'EARNINGS','Fact consent test',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,'CONFIRMED',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, event);
        jdbc.update("""
                INSERT INTO evidence(id,source_id,evidence_type,original_url,content_hash,collected_at,revision,status)
                VALUES(?,?,'DISCLOSURE',?,decode('01','hex'),CURRENT_TIMESTAMP,1,'ACTIVE')
                """, evidence, source, "https://fact-consent.test/"+evidence);
        return new Fixture(source,entity,event,evidence);
    }

    private UUID createEntity() {
        UUID id=UUID.randomUUID();
        jdbc.update("""
                INSERT INTO entity(id,entity_type,canonical_name,canonical_key,active,created_at,updated_at)
                VALUES(?,'COMPANY','Fact consent company',?,true,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id, "COMPANY:"+id);
        return id;
    }

    private UUID createUser(String prefix) {
        UUID id=UUID.randomUUID(); String nick=prefix+id.toString().replace("-", "").substring(0,8);
        jdbc.update("""
                INSERT INTO app_user(id,nickname,nickname_normalized,status,created_at,updated_at)
                VALUES(?,?,?,'ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)
                """, id,nick,nick.toLowerCase(java.util.Locale.ROOT));
        return id;
    }

    private void insertFact(UUID id, Fixture f, String status) {
        if ("SUPPORTED".equals(status)) {
            jdbc.update("""
                    INSERT INTO fact(id,subject_entity_id,event_id,predicate,status,value_type,value_number,currency_code,period_start,period_end,dedup_key,status_reason)
                    VALUES(?,?,?,'REVENUE','SUPPORTED','NUMBER',100,'KRW',DATE '2025-01-01',DATE '2025-12-31',?,NULL)
                    """, id,f.entity(),f.event(),randomKey());
        } else {
            jdbc.update("""
                    INSERT INTO fact(id,subject_entity_id,event_id,predicate,status,value_type,currency_code,period_start,period_end,dedup_key,status_reason)
                    VALUES(?,?,?,'REVENUE','CONFLICTING','NUMBER','KRW',DATE '2025-01-01',DATE '2025-12-31',?,'ASSERTED_VALUE_CONFLICT')
                    """, id,f.entity(),f.event(),randomKey());
        }
    }

    private void insertAssertion(UUID fact, UUID evidence) {
        jdbc.update("""
                INSERT INTO fact_assertion(fact_id,evidence_id,locator,value_type,value_number)
                VALUES(?,?,'table:revenue','NUMBER',100)
                """, fact,evidence);
    }

    private byte[] randomKey() { byte[] key=new byte[32]; new java.security.SecureRandom().nextBytes(key); return key; }
    private void tx(Runnable work) { new TransactionTemplate(transactions).executeWithoutResult(status -> work.run()); }
    private int count(String sql,Object...args){return jdbc.queryForObject(sql,Integer.class,args);}
    private void cleanup(Fixture f) {
        jdbc.update("DELETE FROM fact_assertion WHERE fact_id IN (SELECT id FROM fact WHERE subject_entity_id=?)",f.entity());
        jdbc.update("DELETE FROM fact WHERE subject_entity_id=?",f.entity());
        jdbc.update("DELETE FROM evidence WHERE id=?",f.evidence());
        jdbc.update("DELETE FROM event WHERE id=?",f.event());
        jdbc.update("DELETE FROM entity WHERE id=?",f.entity());
        jdbc.update("DELETE FROM source WHERE id=?",f.source());
    }
    private record Fixture(UUID source,UUID entity,UUID event,UUID evidence){}
}
