package com.aira.api.market.krx;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.repository.MarketEntityRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KrxCurrentQuery {
    public record Observation(UUID factId, LocalDate tradingDate, BigDecimal value) {}
    public record Eligibility(Observation close, BigDecimal dailyChangePercent) {}
    private final KrxLatestCompletedTradingDayResolver resolver;
    private final KrxClient client;
    private final MarketEntityRepository entities;
    private final JdbcTemplate jdbc;
    public KrxCurrentQuery(KrxLatestCompletedTradingDayResolver resolver, KrxClient client,
            MarketEntityRepository entities, JdbcTemplate jdbc) {
        this.resolver = resolver; this.client = client; this.entities = entities; this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Observation find(UUID securityId, FactPredicate predicate) {
        var security = requireSecurity(securityId);
        KrxSnapshot daily = resolver.resolve(security.getMarketCode());
        return exact(securityId, predicate, daily);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Eligibility eligibility(UUID securityId) {
        var security = requireSecurity(securityId);
        KrxSnapshot daily = resolver.resolve(security.getMarketCode());
        Observation close = exact(securityId, FactPredicate.CLOSE_PRICE, daily);
        String standard = jdbc.queryForObject("""
                SELECT identifier_value FROM entity_external_identifier
                WHERE entity_id=? AND namespace='KRX' AND identifier_type='STANDARD_CODE'
                """, String.class, securityId);
        KrxDataset baseDataset = daily.dataset() == KrxDataset.STK_DAILY
                ? KrxDataset.STK_BASE : KrxDataset.KSQ_BASE;
        KrxSnapshot base = client.fetch(baseDataset, daily.date());
        // Full same-date code mapping proves that a daily short code belongs to this canonical SECURITY.
        var packet = KrxPreparedPacket.stock(base, daily);
        String shortCode = packet.securities().stream().filter(s -> s.standardCode().equals(standard))
                .map(KrxPreparedPacket.SecurityRow::shortCode).findFirst()
                .orElseThrow(() -> new IllegalStateException("Security is absent from exact KRX D snapshot"));
        String raw = null;
        for (var row : daily.rows()) if (shortCode.equals(row.get("ISU_CD"))) raw = row.get("FLUC_RT");
        if (raw == null || raw.isBlank() || raw.trim().equals("-")
                || !raw.trim().matches("[+-]?[0-9]+(?:\\.[0-9]+)?"))
            throw new IllegalStateException("Exact KRX D daily change is unavailable");
        return new Eligibility(close, new BigDecimal(raw.trim()));
    }

    private com.aira.api.market.domain.MarketEntity requireSecurity(UUID id) {
        var entity = entities.findById(id).orElseThrow(() -> new IllegalArgumentException("Security is missing"));
        if (entity.getEntityType() != EntityType.SECURITY || !entity.isActive()
                || !("KOSPI".equals(entity.getMarketCode()) || "KOSDAQ".equals(entity.getMarketCode())))
            throw new IllegalArgumentException("Approved KOSPI/KOSDAQ SECURITY is required");
        return entity;
    }

    private Observation exact(UUID securityId, FactPredicate predicate, KrxSnapshot daily) {
        if (predicate == null || !List.of(FactPredicate.OPEN_PRICE, FactPredicate.HIGH_PRICE,
                FactPredicate.LOW_PRICE, FactPredicate.CLOSE_PRICE, FactPredicate.TRADING_VOLUME,
                FactPredicate.TRADING_VALUE, FactPredicate.MARKET_CAP, FactPredicate.LISTED_SHARES).contains(predicate))
            throw new IllegalArgumentException("Approved KRX market predicate is required");
        var rows = jdbc.query("""
                SELECT DISTINCT f.id,f.value_number FROM fact f
                JOIN fact_assertion fa ON fa.fact_id=f.id
                JOIN evidence e ON e.id=fa.evidence_id
                JOIN source s ON s.id=e.source_id
                WHERE f.subject_entity_id=? AND f.predicate=? AND f.period_start=? AND f.period_end=?
                  AND f.status='SUPPORTED' AND f.value_number IS NOT NULL
                  AND s.source_type='EXCHANGE' AND s.external_key='krx' AND e.external_id=?
                  AND e.content_hash=?
                """, (rs, n) -> new Observation(rs.getObject(1, UUID.class), daily.date(), rs.getBigDecimal(2)),
                securityId, predicate.name(), daily.date(), daily.date(),
                "KRX_OPENAPI:" + daily.dataset().apiId() + ":" + daily.basDd(), daily.hash());
        // D is market-wide. A missing target Fact at D is an exact miss, never a D-1 query.
        if (rows.isEmpty()) throw new KrxCurrentExactMissException(daily.date());
        if (rows.size() != 1) throw new KrxCurrentAmbiguousException();
        return rows.getFirst();
    }
}
