package com.aira.api.market.krx;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.repository.MarketEntityRepository;
import com.aira.api.user.repository.AppUserRepository;
import com.aira.api.user.repository.UserInterestRepository;
import com.aira.api.user.service.UserInterestService;
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
class KrxPersistencePostgresTests {
    @Autowired KrxPersistence persistence;
    @Autowired JdbcTemplate jdbc;
    @Autowired MarketEntityRepository entities;
    @Autowired AppUserRepository users;
    @Autowired UserInterestRepository interestRows;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test void exactDOfficialStockAt1000CanBeNewInterestAndRetrySurvives() {
        LocalDate d = LocalDate.of(2035, 1, 12);
        var base = KrxSnapshot.validated(KrxDataset.STK_BASE, d, List.of(
                Map.of("ISU_CD", "KR7000035012", "ISU_SRT_CD", "000012", "ISU_NM", "Twelve")));
        var daily = KrxSnapshot.validated(KrxDataset.STK_DAILY, d, List.of(
                Map.of("BAS_DD", "20350112", "ISU_CD", "000012", "TDD_CLSPRC", "1000", "FLUC_RT", "19.99")));
        persistence.stock(KrxPreparedPacket.stock(base, daily));
        var securityId = jdbc.queryForObject("""
                SELECT entity_id FROM entity_external_identifier
                WHERE namespace='KRX' AND identifier_type='STANDARD_CODE' AND identifier_value='KR7000035012'
                """, java.util.UUID.class);
        KrxClient client = (dataset, date) -> dataset == KrxDataset.STK_BASE ? base : daily;
        var resolver = new KrxLatestCompletedTradingDayResolver(client,
                Clock.fixed(Instant.parse("2035-01-12T02:00:00Z"), ZoneOffset.UTC));
        var interest = new UserInterestService(interestRows, entities, users,
                new KrxCurrentQuery(resolver, client, entities, jdbc));
        java.util.UUID userId = java.util.UUID.randomUUID();
        String nickname = "krx" + userId.toString().replace("-", "").substring(0, 8);
        jdbc.update("INSERT INTO app_user(id,nickname,nickname_normalized,status) VALUES(?,?,?,'ACTIVE')",
                userId, nickname, nickname);
        var tx = new org.springframework.transaction.support.TransactionTemplate(transactions);
        try {
            assertEquals(securityId, tx.execute(status -> interest.add(userId, securityId)).entityId());
            assertEquals(securityId, tx.execute(status -> interest.add(userId, securityId)).entityId());
            assertEquals(1, count("SELECT count(*) FROM user_interest WHERE user_id=?", userId));
            tx.executeWithoutResult(status -> interest.remove(userId, securityId));
            assertEquals(0, count("SELECT count(*) FROM user_interest WHERE user_id=?", userId));
        } finally {
            jdbc.update("DELETE FROM user_interest WHERE user_id=?", userId);
            jdbc.update("DELETE FROM app_user WHERE id=?", userId);
        }
    }

    @Test void currentUsesOnlyMarketDAndNeverFallsBackForMissingTargetFact() {
        LocalDate prior = LocalDate.of(2035, 1, 10), d = prior.plusDays(1);
        var oldPacket = packet(prior, "900", "10", "Target");
        var marketPacket = packet(d, "1500", "10", "Other");
        persistence.stock(oldPacket);
        persistence.stock(marketPacket);
        var targetId = jdbc.queryForObject("""
                SELECT entity_id FROM entity_external_identifier
                WHERE namespace='KRX' AND identifier_type='STANDARD_CODE' AND identifier_value=?
                """, java.util.UUID.class, "KR7000035010");
        var otherId = jdbc.queryForObject("""
                SELECT entity_id FROM entity_external_identifier
                WHERE namespace='KRX' AND identifier_type='STANDARD_CODE' AND identifier_value=?
                """, java.util.UUID.class, "KR7000035011");
        KrxClient client = (dataset, date) -> KrxSnapshot.validated(dataset, date,
                dataset == KrxDataset.STK_DAILY && date.equals(d) ? marketPacket.daily().rows() : List.of());
        var resolver = new KrxLatestCompletedTradingDayResolver(client,
                Clock.fixed(Instant.parse("2035-01-11T02:00:00Z"), ZoneOffset.UTC));
        var current = new KrxCurrentQuery(resolver, client, entities, jdbc);
        assertEquals(d, current.find(otherId, FactPredicate.CLOSE_PRICE).tradingDate());
        assertThrows(IllegalStateException.class, () -> current.find(targetId, FactPredicate.CLOSE_PRICE));
    }

    @Test void sharedEvidenceAndIdenticalReplay() {
        var packet = packet(LocalDate.of(2035, 1, 2), "100", "200", "One");
        persistence.stock(packet);
        persistence.stock(packet);
        String id = "KRX_OPENAPI:stk_bydd_trd:20350102";
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", id));
        assertEquals(2, count("SELECT count(*) FROM fact_assertion fa JOIN evidence e ON e.id=fa.evidence_id WHERE e.external_id=?", id));
        assertEquals(1, count("SELECT count(*) FROM entity_external_identifier WHERE namespace='KRX' AND identifier_type='STANDARD_CODE' AND identifier_value=?", "KR7000035002"));
        assertEquals(0, count("SELECT count(*) FROM entity_external_identifier x JOIN entity e ON e.id=x.entity_id WHERE x.identifier_value=? AND e.entity_type='COMPANY'", "KR7000035002"));
    }
    @Test void changedSnapshotBlocksWithoutOverwriting() {
        var date = LocalDate.of(2035, 1, 3);
        persistence.stock(packet(date, "100", "200", "Two"));
        assertThrows(IllegalStateException.class, () -> persistence.stock(packet(date, "101", "200", "Two")));
        assertEquals(2, count("SELECT count(*) FROM fact_assertion fa JOIN evidence e ON e.id=fa.evidence_id WHERE e.external_id=?",
                "KRX_OPENAPI:stk_bydd_trd:20350103"));
    }
    @Test void laterFailureRollsBackBothSnapshotsAndIdentity() {
        var date = LocalDate.of(2035, 1, 4);
        var bad = packet(date, "100", "200", "");
        assertThrows(IllegalArgumentException.class, () -> persistence.stock(bad));
        assertEquals(0, count("SELECT count(*) FROM evidence WHERE external_id=?", "KRX_OPENAPI:stk_bydd_trd:20350104"));
        assertEquals(0, count("SELECT count(*) FROM evidence WHERE external_id=?", "KRX_OPENAPI:stk_isu_base_info:20350104"));
        assertEquals(0, count("SELECT count(*) FROM entity_external_identifier WHERE identifier_value=?", "KR7000035004"));
    }
    @Test void differentSnapshotSameFactMarksConflict() {
        var date = LocalDate.of(2035, 1, 5);
        persistence.stock(packet(date, "100", "200", "Three"));
        var base = KrxSnapshot.validated(KrxDataset.KSQ_BASE, date, List.of(
                Map.of("ISU_CD", "KR7000035005", "ISU_SRT_CD", "000005", "ISU_NM", "Three")));
        var daily = KrxSnapshot.validated(KrxDataset.KSQ_DAILY, date, List.of(
                Map.of("BAS_DD", "20350105", "ISU_CD", "000005", "TDD_CLSPRC", "101", "ACC_TRDVOL", "200")));
        persistence.stock(KrxPreparedPacket.stock(base, daily));
        assertEquals(1, count("SELECT count(*) FROM fact WHERE predicate='CLOSE_PRICE' AND status='CONFLICTING' AND period_start=?", date));
        assertEquals(2, count("SELECT count(*) FROM fact_assertion fa JOIN fact f ON f.id=fa.fact_id WHERE f.predicate='CLOSE_PRICE' AND f.period_start=?", date));
    }
    @Test void indexSnapshotRegistersEvidenceOnly() {
        var date = LocalDate.of(2035, 1, 6);
        var index = KrxSnapshot.validated(KrxDataset.KOSPI_INDEX, date,
                List.of(Map.of("BAS_DD", "20350106", "IDX_CLSS", "KOSPI", "IDX_NM", "KOSPI")));
        persistence.index(index);
        persistence.index(index);
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", "KRX_OPENAPI:kospi_dd_trd:20350106"));
        assertEquals(0, count("SELECT count(*) FROM fact f JOIN fact_assertion fa ON fa.fact_id=f.id JOIN evidence e ON e.id=fa.evidence_id WHERE e.external_id=?", "KRX_OPENAPI:kospi_dd_trd:20350106"));
    }
    @Test void oneDailySnapshotSupportsMultipleSecurities() {
        var date = LocalDate.of(2035, 1, 7);
        var base = KrxSnapshot.validated(KrxDataset.STK_BASE, date, List.of(
                Map.of("ISU_CD", "KR7000035007", "ISU_SRT_CD", "000007", "ISU_NM", "Seven"),
                Map.of("ISU_CD", "KR7000035008", "ISU_SRT_CD", "000008", "ISU_NM", "Eight")));
        var daily = KrxSnapshot.validated(KrxDataset.STK_DAILY, date, List.of(
                Map.of("BAS_DD", "20350107", "ISU_CD", "000007", "TDD_CLSPRC", "10"),
                Map.of("BAS_DD", "20350107", "ISU_CD", "000008", "TDD_CLSPRC", "20")));
        persistence.stock(KrxPreparedPacket.stock(base, daily));
        String externalId = "KRX_OPENAPI:stk_bydd_trd:20350107";
        assertEquals(1, count("SELECT count(*) FROM evidence WHERE external_id=?", externalId));
        assertEquals(2, count("SELECT count(*) FROM fact_assertion fa JOIN evidence e ON e.id=fa.evidence_id WHERE e.external_id=?", externalId));
    }
    private KrxPreparedPacket packet(LocalDate date, String close, String volume, String name) {
        String suffix = String.format("%02d", date.getDayOfMonth());
        String standard = "KR70000350" + suffix;
        String shortCode = "0000" + suffix;
        var base = KrxSnapshot.validated(KrxDataset.STK_BASE, date, List.of(
                Map.of("ISU_CD", standard, "ISU_SRT_CD", shortCode, "ISU_NM", name)));
        var daily = KrxSnapshot.validated(KrxDataset.STK_DAILY, date, List.of(
                Map.of("BAS_DD", date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                        "ISU_CD", shortCode, "TDD_CLSPRC", close, "ACC_TRDVOL", volume)));
        return KrxPreparedPacket.stock(base, daily);
    }
    private int count(String sql, Object value) { return jdbc.queryForObject(sql, Integer.class, value); }
}
