package com.aira.api.market.query;

import com.aira.api.market.domain.FactPredicate;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialHistoricalExactQuery {
    private final CompanyFinancialFactsQuery financials;
    private final JdbcTemplate jdbc;

    public FinancialHistoricalExactQuery(CompanyFinancialFactsQuery financials, JdbcTemplate jdbc) {
        this.financials = financials;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public CompanyFinancialFactsResult find(UUID companyId, LocalDate start, LocalDate end,
            String receipt, Set<String> predicates) {
        if (receipt == null || !receipt.matches("[0-9]{14}"))
            throw new IllegalArgumentException("Exact OpenDART receipt is required");
        var all = financials.find(new CompanyFinancialFactsQueryInput(companyId, predicates, start, end));
        List<CompanyFinancialFactView> selected = all.facts().stream()
                .filter(view -> receipt.equals(view.evidenceExternalId()))
                .toList();
        if (selected.isEmpty()) throw missing();
        for (var view : selected) {
            Integer links = jdbc.queryForObject("""
                    SELECT count(*) FROM fact f
                    JOIN fact_assertion fa ON fa.fact_id=f.id
                    JOIN evidence value_e ON value_e.id=fa.evidence_id
                    JOIN source value_s ON value_s.id=value_e.source_id
                    JOIN fact_period_evidence pe ON pe.fact_id=f.id
                    JOIN evidence period_e ON period_e.id=pe.evidence_id
                    JOIN source period_s ON period_s.id=period_e.source_id
                    WHERE f.subject_entity_id=? AND f.predicate=?
                      AND f.period_start=? AND f.period_end=?
                      AND value_s.source_type='REGULATOR' AND value_s.external_key='opendart'
                      AND value_e.external_id=? AND fa.evidence_id=?
                      AND period_s.source_type='REGULATOR' AND period_s.external_key='opendart'
                      AND period_e.external_id=?
                    """, Integer.class, companyId, view.predicate().name(), start, end, receipt,
                    view.evidenceId(),
                    "fnlttSinglAcnt:CFS:11011:" + receipt);
            // A matching value alone cannot turn an inferred legacy period into Historical Exact.
            if (links == null || links == 0) throw missing();
        }
        return new CompanyFinancialFactsResult(companyId, selected);
    }

    private static CompanyFinancialFactsQueryException missing() {
        return new CompanyFinancialFactsQueryException(
                CompanyFinancialFactsQueryException.Category.FACTS_NOT_FOUND,
                "No financial facts have the exact filing and verified period evidence");
    }
}
