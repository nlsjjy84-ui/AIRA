package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.*;

import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.domain.EventType;
import com.aira.api.market.domain.EventOrigin;
import com.aira.api.market.ingestion.EvidenceRegistration;
import com.aira.api.market.service.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
class OpenDartMaterialEventRegistrationPostgresTests {
    @Autowired OpenDartMaterialEventRegistration registration;
    @Autowired CompanyEntityBootstrapOperation companies;
    @Autowired EntityExternalIdentifierRegistryService identifiers;
    @Autowired JdbcTemplate jdbc;
    @Autowired OpenDartDs005Parser ds005Parser;

    @Test void backfillOriginPersistsAndCannotBePromotedByLiveRetry() {
        Fixture f = fixture();
        var input = f.input();
        var backfill = new ValidatedMaterialEvent(input.endpointKey(), input.corpCode(),
                input.receiptNumber(), input.eventType(), input.neutralTitle(),
                input.structuredEvidence(), input.observedAt(), EventOrigin.BACKFILL);
        UUID id = registration.register(backfill);
        assertEquals(id, registration.register(new ValidatedMaterialEvent(input.endpointKey(), input.corpCode(),
                input.receiptNumber(), input.eventType(), input.neutralTitle(),
                input.structuredEvidence(), input.observedAt(), EventOrigin.LIVE)));
        assertEquals("BACKFILL", jdbc.queryForObject("SELECT ingestion_origin FROM event WHERE id=?", String.class, id));
    }

    @Test void ds005WholeReceiptRegistersOneConfirmedEventAndConflictsOnChangedPayload() {
        Fixture f = fixture();
        var request = new OpenDartDs005Request("piicDecsn", f.corp,
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31), EventOrigin.LIVE);
        String firstBody = "{\"status\":\"000\",\"message\":\"ok\",\"list\":["
                + "{\"corp_code\":\"" + f.corp + "\",\"rcept_no\":\"" + f.receipt + "\",\"x\":\"a\"},"
                + "{\"corp_code\":\"" + f.corp + "\",\"rcept_no\":\"" + f.receipt + "\",\"x\":\"b\"}]}";
        var receipt = ds005Parser.parse(request, firstBody, f.observedAt).receipts().getFirst();
        UUID id = registration.register(receipt);
        assertEquals(id, registration.register(receipt));
        assertEquals("DISCLOSURE", jdbc.queryForObject("SELECT event_type FROM event WHERE id=?", String.class, id));
        assertEquals("LIVE", jdbc.queryForObject("SELECT ingestion_origin FROM event WHERE id=?", String.class, id));
        assertEquals("유상증자 결정", jdbc.queryForObject("SELECT title FROM event WHERE id=?", String.class, id));
        assertNull(jdbc.queryForObject("SELECT occurred_at FROM event WHERE id=?", OffsetDateTime.class, id));
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", receipt.structuredEvidence().externalId()));
        // A confirmed Material Event is upstream evidence, not an implicit completed judgment or delivery trigger.
        assertEquals(0, count("SELECT count(*) FROM assessment WHERE event_id=?", id));
        assertEquals(0, count("SELECT count(*) FROM alert al JOIN assessment a ON a.id=al.assessment_id WHERE a.event_id=?", id));
        assertEquals(0, count("SELECT count(*) FROM briefing_item bi JOIN assessment a ON a.id=bi.assessment_id WHERE a.event_id=?", id));
        var changed = ds005Parser.parse(request, firstBody.replace("\"b\"", "\"c\""),
                f.observedAt).receipts().getFirst();
        assertThrows(IllegalStateException.class, () -> registration.register(changed));
        assertEquals(1, count("SELECT count(*) FROM event WHERE id=?", id));
        var retryAsBackfill = new ValidatedMaterialEvent(receipt.endpointKey(), receipt.corpCode(),
                receipt.receiptNumber(), receipt.eventType(), receipt.neutralTitle(),
                receipt.structuredEvidence(), receipt.observedAt(), EventOrigin.BACKFILL);
        assertEquals(id, registration.register(retryAsBackfill));
        assertEquals("LIVE", jdbc.queryForObject("SELECT ingestion_origin FROM event WHERE id=?", String.class, id));
        assertEquals(0, count("SELECT count(*) FROM assessment WHERE event_id=?", id));
    }

    @Test void createsCandidateLinksThenConfirmsAndReplaysIdempotently() throws Exception {
        Fixture f = fixture();
        UUID first = registration.register(f.input());
        UUID second = registration.register(f.input());
        assertEquals(first, second);
        assertEquals("LEGACY_UNKNOWN", jdbc.queryForObject("SELECT ingestion_origin FROM event WHERE id=?", String.class, first));
        assertEquals("GOVERNANCE", jdbc.queryForObject("SELECT event_type FROM event WHERE id=?", String.class, first));
        assertEquals("CONFIRMED", jdbc.queryForObject("SELECT status FROM event WHERE id=?", String.class, first));
        assertNull(jdbc.queryForObject("SELECT occurred_at FROM event WHERE id=?", OffsetDateTime.class, first));
        assertEquals(1, count("SELECT count(*) FROM event_entity WHERE event_id=? AND relation_type='SUBJECT'", first));
        assertEquals(1, count("SELECT count(*) FROM event_evidence WHERE event_id=? AND relation_type='SUPPORTS'", first));
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", f.identity()));
        assertEquals(0, count("SELECT count(*) FROM evidence WHERE external_id=?", f.receipt));
        assertArrayEquals(OpenDartMaterialEventRegistration.dedup(f.endpoint, f.canonicalKey, f.receipt),
                jdbc.queryForObject("SELECT dedup_key FROM event WHERE id=?", byte[].class, first));
    }

    @Test void sameDedupWithDifferentTitleOrEvidenceBlocksWithoutMutation() throws Exception {
        Fixture f = fixture();
        UUID id = registration.register(f.input());
        assertThrows(IllegalStateException.class, () -> registration.register(f.input("Changed title", "original")));
        assertThrows(IllegalStateException.class, () -> registration.register(f.input("Neutral decision", "changed")));
        assertEquals("Neutral decision", jdbc.queryForObject("SELECT title FROM event WHERE id=?", String.class, id));
        assertEquals(1, count("SELECT count(*) FROM event_evidence WHERE event_id=?", id));
    }

    @Test void concurrentIdenticalRegistrationReusesOneEventAndLinks() throws Exception {
        Fixture f = fixture();
        var start = new CountDownLatch(1);
        try (var threads = Executors.newFixedThreadPool(2)) {
            var a = threads.submit(() -> { start.await(); return registration.register(f.input()); });
            var b = threads.submit(() -> { start.await(); return registration.register(f.input()); });
            start.countDown();
            assertEquals(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        }
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", f.identity()));
        assertEquals(1, count("SELECT count(*) FROM event WHERE dedup_key=?",
                OpenDartMaterialEventRegistration.dedup(f.endpoint, f.canonicalKey, f.receipt)));
    }

    @Test void relationFailureRollsBackEvidenceAndEventAndOtherEndpointCannotReuseReceipt() throws Exception {
        Fixture f = fixture();
        String constraint = "test_material_support_" + f.corp;
        jdbc.execute("ALTER TABLE event_evidence ADD CONSTRAINT " + constraint
                + " CHECK (relation_type <> 'SUPPORTS') NOT VALID");
        try {
            assertThrows(RuntimeException.class, () -> registration.register(f.input()));
            assertEquals(0, count("SELECT count(*) FROM evidence WHERE external_id=?", f.identity()));
            assertEquals(0, count("SELECT count(*) FROM event WHERE dedup_key=?",
                    OpenDartMaterialEventRegistration.dedup(f.endpoint, f.canonicalKey, f.receipt)));
        } finally {
            jdbc.execute("ALTER TABLE event_evidence DROP CONSTRAINT " + constraint);
        }
        registration.register(f.input());
        var other = new ValidatedMaterialEvent("otherEndpoint", f.corp, f.receipt,
                EventType.GOVERNANCE, "Neutral decision", f.evidence("otherEndpoint", "original"), f.observedAt);
        assertThrows(IllegalStateException.class, () -> registration.register(other));
    }

    private Fixture fixture() {
        String corp = String.format("%08d", Math.floorMod(UUID.randomUUID().getMostSignificantBits(), 100_000_000L));
        String receipt = String.format("%014d", Math.floorMod(UUID.randomUUID().getLeastSignificantBits(), 100_000_000_000_000L));
        var company = companies.create(new CompanyEntityBootstrapCommand("Material Fixture", "KR"));
        identifiers.registerOrReuse(new ExternalIdentifierRegistration(company.entityId(),
                new ExternalIdentifierKey("OPENDART", "CORP_CODE", corp)));
        return new Fixture(corp, receipt, company.canonicalKey());
    }

    private int count(String sql, Object... args) { return jdbc.queryForObject(sql, Integer.class, args); }

    private static final class Fixture {
        final String corp, receipt, canonicalKey, endpoint = "sampleEndpoint";
        final OffsetDateTime observedAt = OffsetDateTime.parse("2026-09-14T00:00:00Z");
        Fixture(String corp, String receipt, String canonicalKey) {
            this.corp = corp; this.receipt = receipt; this.canonicalKey = canonicalKey;
        }
        String identity() { return "OPENDART_MATERIAL:" + endpoint + ":" + receipt; }
        ValidatedMaterialEvent input() { return input("Neutral decision", "original"); }
        ValidatedMaterialEvent input(String title, String content) {
            return new ValidatedMaterialEvent(endpoint, corp, receipt, EventType.GOVERNANCE,
                    title, evidence(endpoint, content), observedAt);
        }
        EvidenceRegistration evidence(String endpointKey, String content) {
            try {
                return new EvidenceRegistration(EvidenceType.OFFICIAL_DATA,
                        "OPENDART_MATERIAL:" + endpointKey + ":" + receipt,
                        "https://opendart.fss.or.kr/api/" + endpointKey + ".json?corp_code=" + corp,
                        "OpenDART structured material row", MessageDigest.getInstance("SHA-256")
                        .digest(content.getBytes(StandardCharsets.UTF_8)), null, null, observedAt, 1);
            } catch (Exception impossible) { throw new IllegalStateException(impossible); }
        }
    }
}
