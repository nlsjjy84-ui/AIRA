package com.aira.api.market.krx;

import com.aira.api.market.domain.*;
import com.aira.api.market.ingestion.EvidenceRegistration;
import com.aira.api.market.repository.*;
import com.aira.api.market.service.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KrxPersistence {
    private final JdbcTemplate jdbc;
    private final SourceRegistryService sourceRegistry;
    private final EvidenceRegistrationStore evidenceRegistrations;
    private final EvidenceRepository evidences;
    private final MarketEntityRepository entities;
    private final EntityExternalIdentifierRegistryService identifiers;
    private final FactRepository facts;
    private final FactAssertionRepository assertions;

    public KrxPersistence(JdbcTemplate jdbc, SourceRegistryService sourceRegistry,
            EvidenceRegistrationStore evidenceRegistrations, EvidenceRepository evidences,
            MarketEntityRepository entities, EntityExternalIdentifierRegistryService identifiers,
            FactRepository facts, FactAssertionRepository assertions) {
        this.jdbc = jdbc;
        this.sourceRegistry = sourceRegistry;
        this.evidenceRegistrations = evidenceRegistrations;
        this.evidences = evidences;
        this.entities = entities;
        this.identifiers = identifiers;
        this.facts = facts;
        this.assertions = assertions;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void stock(KrxPreparedPacket packet) {
        if (packet == null) throw new IllegalArgumentException("Prepared KRX stock packet is required");
        // Serialize KRX writers so lookup/create and assertion conflict checks see the preceding commit.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended('KRX:STOCK',0))", Object.class);
        Source source = source();
        registerScopes(source);
        registerEvidence(source, packet.base());
        Evidence dailyEvidence = registerEvidence(source, packet.daily());
        Map<String, MarketEntity> resolved = new HashMap<>();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        for (var security : packet.securities()) {
            resolved.computeIfAbsent(security.standardCode(), standard -> resolve(security, now));
        }
        for (var metric : packet.values()) {
            MarketEntity subject = resolved.get(metric.standardCode());
            byte[] key = factKey(subject, metric, packet.daily());
            Fact fact = facts.findByDedupKey(key).orElse(null);
            if (fact == null) fact = facts.saveAndFlush(Fact.supportedMarketNumber(subject,
                    metric.predicate(), metric.value(), packet.daily().date(), key, now));
            FactAssertionId assertionId = new FactAssertionId(fact.getId(), dailyEvidence.getId());
            if (assertions.existsById(assertionId)) continue;
            if (fact.getStatus() == FactStatus.SUPPORTED && fact.getValueNumber().compareTo(metric.value()) != 0)
                fact.markConflicting(now);
            else if (fact.getStatus() == FactStatus.UNKNOWN) throw new IllegalStateException("Unknown Market Fact cannot be resolved");
            assertions.save(FactAssertion.assertedNumber(fact, dailyEvidence,
                    "OutBlock_1/ISU_SRT_CD=" + metric.shortCode() + "/" + metric.field(), metric.value(), now));
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void index(KrxSnapshot snapshot) {
        KrxIndexPacket packet = KrxIndexPacket.from(snapshot);
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", Object.class,
                "KRX:INDEX:" + packet.market());
        Source source = source();
        registerScopes(source);
        Evidence evidence = registerEvidence(source, snapshot);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        MarketEntity market = resolveMarket(packet.market(), now);
        for (var metric : packet.values()) {
            byte[] key = indexFactKey(market, metric, snapshot);
            Fact fact = facts.findByDedupKey(key).orElse(null);
            if (fact == null) fact = facts.saveAndFlush(Fact.supportedMarketIndexNumber(market,
                    metric.predicate(), metric.value(), snapshot.date(), key, now));
            FactAssertionId assertionId = new FactAssertionId(fact.getId(), evidence.getId());
            if (assertions.existsById(assertionId)) continue;
            if (fact.getStatus() == FactStatus.SUPPORTED && fact.getValueNumber().compareTo(metric.value()) != 0)
                fact.markConflicting(now);
            else if (fact.getStatus() == FactStatus.UNKNOWN) throw new IllegalStateException("Unknown index Fact cannot be resolved");
            assertions.save(FactAssertion.assertedNumber(fact, evidence,
                    "OutBlock_1/IDX_NM=" + packet.market() + "/" + metric.field(), metric.value(), now));
        }
    }

    private Source source() {
        Integer incompatible = jdbc.queryForObject("""
                SELECT count(*) FROM source WHERE external_key='krx'
                  AND (source_type<>'EXCHANGE' OR name<>'KRX Data Marketplace Open API'
                       OR canonical_domain IS DISTINCT FROM 'openapi.krx.co.kr')
                """, Integer.class);
        if (incompatible != 0) throw new IllegalStateException("KRX source provenance conflicts");
        return sourceRegistry.registerOrReuse(new SourceRegistration(SourceType.EXCHANGE,
                "krx", "KRX Data Marketplace Open API", "openapi.krx.co.kr"));
    }

    private void registerScopes(Source source) {
        for (String scope : new String[] {"LISTING_STATUS", "MARKET_TRADING_DATA"}) {
            int written = jdbc.update("""
                    INSERT INTO source_authority_scope(source_id,scope_type,authority_role,
                        subject_entity_id,jurisdiction_entity_id,basis_url,verified_at)
                    VALUES (?,?,'OFFICIAL_OPERATOR',NULL,NULL,
                        'https://openapi.krx.co.kr/contents/OPP/INFO/OPPINFO001.jsp',CURRENT_TIMESTAMP)
                    ON CONFLICT (source_id,scope_type,authority_role,subject_entity_id,jurisdiction_entity_id)
                    DO UPDATE SET basis_url=EXCLUDED.basis_url
                    WHERE source_authority_scope.basis_url=EXCLUDED.basis_url
                    """, source.getId(), scope);
            if (written != 1) throw new IllegalStateException("KRX authority scope conflicts");
        }
    }

    private Evidence registerEvidence(Source source, KrxSnapshot snapshot) {
        String basDd = snapshot.basDd();
        var registration = new EvidenceRegistration(EvidenceType.OFFICIAL_DATA,
                "KRX_OPENAPI:" + snapshot.dataset().apiId() + ":" + basDd,
                snapshot.dataset().url(basDd), "KRX " + snapshot.dataset().apiId() + " daily snapshot",
                snapshot.hash(), null, null, OffsetDateTime.now(ZoneOffset.UTC), 1);
        UUID id = evidenceRegistrations.registerOrGetId(source, registration);
        return evidences.findById(id).orElseThrow();
    }

    private MarketEntity resolve(KrxPreparedPacket.SecurityRow row, OffsetDateTime now) {
        var key = new ExternalIdentifierKey("KRX", "STANDARD_CODE", row.standardCode());
        MarketEntity existing = identifiers.findEntity(key).orElse(null);
        if (existing != null) {
            if (existing.getEntityType() != EntityType.SECURITY) throw new IllegalStateException("KRX code mapped to non-security");
            return existing;
        }
        // The standard code identifies the SECURITY; the dated short code and name only initialize display metadata.
        MarketEntity created = entities.saveAndFlush(MarketEntity.security(row.name(), row.market(),
                row.shortCode(), UUID.randomUUID(), now));
        identifiers.registerOrReuse(new ExternalIdentifierRegistration(created.getId(), key));
        return created;
    }

    private MarketEntity resolveMarket(String marketCode, OffsetDateTime now) {
        String key = "MARKET:KR:" + marketCode;
        MarketEntity existing = entities.findByCanonicalKey(key).orElse(null);
        if (existing != null) {
            if (existing.getEntityType() != EntityType.MARKET || !marketCode.equals(existing.getMarketCode()))
                throw new IllegalStateException("KRX market canonical identity conflicts");
            return existing;
        }
        return entities.saveAndFlush(MarketEntity.market(marketCode, marketCode, "KR", now));
    }

    private static byte[] factKey(MarketEntity subject, KrxPreparedPacket.MarketValue metric, KrxSnapshot daily) {
        String identity = "AIRA|FACT|V1|MARKET_DAILY|" + subject.getCanonicalKey() + "|"
                + metric.predicate() + "|" + daily.date();
        try { return MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }

    private static byte[] indexFactKey(MarketEntity subject, KrxIndexPacket.IndexValue metric,
            KrxSnapshot daily) {
        String identity = "AIRA|FACT|V1|MARKET_INDEX_DAILY|" + subject.getCanonicalKey() + "|"
                + metric.predicate() + "|" + daily.date();
        try { return MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
}
