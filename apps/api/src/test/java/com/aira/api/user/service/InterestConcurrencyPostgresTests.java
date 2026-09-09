package com.aira.api.user.service;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
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
class InterestConcurrencyPostgresTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired UserInterestService interests;

    @Test
    void simultaneousAddsAndEnablesAreIdempotentAndIsolated() throws Exception {
        UUID user = UUID.randomUUID(), other = UUID.randomUUID(), entity = UUID.randomUUID();
        try {
            for (UUID id : List.of(user, other)) {
                String name = "Interest" + id.toString().replace("-", "").substring(0, 8);
                jdbc.update("INSERT INTO app_user(id,nickname,nickname_normalized,status) VALUES(?,?,?,'ACTIVE')",
                        id, name, name.toLowerCase());
            }
            jdbc.update("""
                    INSERT INTO entity(id,entity_type,canonical_name,canonical_key,active)
                    VALUES(?,'COMPANY','Concurrent company',?,true)
                    """, entity, "COMPANY:" + entity);
            runTogether(() -> interests.add(user, entity));
            assertEquals(1, interests.findAll(user).size());
            assertTrue(interests.findAll(other).isEmpty());
            var enabled = runTogether(() -> interests.setAlertEnabled(user, entity, true));
            assertEquals(enabled.getFirst().alertEnabledAt(), enabled.getLast().alertEnabledAt());
            assertNotNull(enabled.getFirst().alertEnabledAt());
            assertEquals(enabled.getFirst().alertEnabledAt(), interests.add(user, entity).alertEnabledAt());
            interests.remove(other, entity);
            assertEquals(1, interests.findAll(user).size());
            runTogether(() -> interests.setAlertEnabled(user, entity, false));
            assertNull(interests.findAll(user).getFirst().alertEnabledAt());
            assertThrows(com.aira.api.user.exception.InterestEntityNotFoundException.class,
                    () -> interests.setAlertEnabled(other, entity, true));
        } finally {
            jdbc.update("DELETE FROM user_interest WHERE user_id IN (?,?)", user, other);
            jdbc.update("DELETE FROM entity WHERE id=?", entity);
            jdbc.update("DELETE FROM app_user WHERE id IN (?,?)", user, other);
        }
    }

    private static <T> List<T> runTogether(Callable<T> action) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var start = new CyclicBarrier(2);
            Callable<T> ready = () -> { start.await(10, TimeUnit.SECONDS); return action.call(); };
            var a = executor.submit(ready);
            var b = executor.submit(ready);
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
    }
}
