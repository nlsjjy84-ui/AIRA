package com.aira.api.market.controller;

import com.aira.api.analysis.query.CurrentAssessmentQuery;
import com.aira.api.analysis.query.HistoricalAssessmentNotFoundException;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.FactStatus;
import com.aira.api.market.dto.*;
import com.aira.api.market.krx.KrxCurrentQuery;
import com.aira.api.market.krx.KrxCurrentExactMissException;
import com.aira.api.market.krx.KrxCurrentAmbiguousException;
import com.aira.api.market.query.*;
import com.aira.api.market.repository.FactRepository;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@RestController
public class CanonicalReadController {
    private final CanonicalEntitySearchQuery search;
    private final FinancialHistoricalExactQuery historical;
    private final FinancialCurrentQuery financialCurrent;
    private final CurrentAssessmentQuery assessmentCurrent;
    private final KrxCurrentQuery krxCurrent;
    private final FactRepository facts;
    private final JdbcTemplate jdbc;

    public CanonicalReadController(CanonicalEntitySearchQuery search,
            FinancialHistoricalExactQuery historical, FinancialCurrentQuery financialCurrent,
            CurrentAssessmentQuery assessmentCurrent, KrxCurrentQuery krxCurrent,
            FactRepository facts, JdbcTemplate jdbc) {
        this.search = search; this.historical = historical; this.financialCurrent = financialCurrent;
        this.assessmentCurrent = assessmentCurrent; this.krxCurrent = krxCurrent; this.facts = facts;
        this.jdbc = jdbc;
    }

    @GetMapping("/api/search")
    public EntitySearchResponse search(@RequestParam String query) { return search.find(query); }

    @GetMapping("/api/companies/{companyId}/financial-facts/exact")
    public CanonicalReadResponse<CompanyFinancialFactsResponse> financialExact(@PathVariable UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam String receipt, @RequestParam(required = false) Set<String> predicates) {
        return financial(companyId, periodStart, periodEnd, receipt, predicates, false);
    }

    @GetMapping("/api/companies/{companyId}/financial-facts/current")
    public CanonicalReadResponse<CompanyFinancialFactsResponse> financialCurrent(@PathVariable UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam String receipt, @RequestParam(required = false) Set<String> predicates) {
        return financial(companyId, periodStart, periodEnd, receipt, predicates, true);
    }

    private CanonicalReadResponse<CompanyFinancialFactsResponse> financial(UUID companyId,
            LocalDate start, LocalDate end, String receipt, Set<String> predicates, boolean current) {
        String selection = current ? "EXPLICIT_FINANCIAL_CURRENT" : "HISTORICAL_EXACT";
        try {
            var result = current
                    ? financialCurrent.find(new FinancialCurrentQuery.ExplicitSelection(companyId, start, end, receipt, predicates))
                    : historical.find(companyId, start, end, receipt, predicates);
            var returned = result.facts().stream().map(CompanyFinancialFactView::predicate).collect(java.util.stream.Collectors.toSet());
            Set<FactPredicate> requested = predicates == null || predicates.isEmpty()
                    ? Set.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME)
                    : predicates.stream().map(FactPredicate::valueOf).collect(java.util.stream.Collectors.toSet());
            CanonicalDataState state = returned.containsAll(requested) ? CanonicalDataState.AVAILABLE
                    : CanonicalDataState.PARTIAL;
            var views = result.facts().stream().map(v -> new CompanyFinancialFactResponse(v.companyId(),
                    v.predicate(), v.value(), v.currency(), v.periodStart(), v.periodEnd(),
                    v.publishedAt(), v.collectedAt(), v.sourceName(), v.evidenceId(),
                    v.evidenceExternalId(), v.evidenceOriginalUrl())).toList();
            return new CanonicalReadResponse<>(state, selection, companyId, "COMPANY", start, end,
                    receipt, new CompanyFinancialFactsResponse(companyId, views));
        } catch (CompanyFinancialFactsQueryException missing) {
            CanonicalDataState state = switch (missing.category()) {
                case FACTS_NOT_FOUND, COMPANY_NOT_FOUND -> CanonicalDataState.NO_DATA;
                case UNSUPPORTED_PREDICATE -> CanonicalDataState.UNSUPPORTED;
                case INCONSISTENT_PROVENANCE -> conflicting(companyId, start, end)
                        ? CanonicalDataState.CONFLICTING : CanonicalDataState.BLOCKED;
            };
            return new CanonicalReadResponse<>(state, selection, companyId, "COMPANY", start, end, receipt, null);
        } catch (IllegalArgumentException invalid) {
            return new CanonicalReadResponse<>(CanonicalDataState.UNSUPPORTED,
                    selection, companyId, "COMPANY", start, end, receipt, null);
        }
    }

    private boolean conflicting(UUID companyId, LocalDate start, LocalDate end) {
        return facts.findBySubjectEntityIdAndPredicateInAndPeriodStartAndPeriodEnd(companyId,
                Set.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME), start, end).stream()
                .anyMatch(f -> f.getStatus() == FactStatus.CONFLICTING);
    }

    @GetMapping("/api/assessments/current")
    public CanonicalReadResponse<com.aira.api.analysis.dto.HistoricalAssessmentResponse> assessmentCurrent(
            @RequestParam UUID eventId) {
        try {
            var value = assessmentCurrent.find(eventId);
            return new CanonicalReadResponse<>(CanonicalDataState.AVAILABLE,
                    "SUPERSESSION_TERMINAL", eventId, "EVENT", null, null, null, value);
        } catch (HistoricalAssessmentNotFoundException absent) {
            Integer candidates = jdbc.queryForObject("SELECT count(*) FROM assessment WHERE event_id=? AND status='COMPLETED'",
                    Integer.class, eventId);
            // A broken/forked graph is blocked; zero completed nodes is a genuine exact absence.
            return new CanonicalReadResponse<>(candidates != null && candidates > 0
                    ? CanonicalDataState.BLOCKED : CanonicalDataState.NO_DATA,
                    "SUPERSESSION_TERMINAL", eventId, "EVENT", null, null, null, null);
        }
    }

    @GetMapping("/api/securities/{securityId}/market-current")
    public CanonicalReadResponse<KrxCurrentQuery.Observation> marketCurrent(@PathVariable UUID securityId,
            @RequestParam FactPredicate predicate) {
        try {
            var value = krxCurrent.find(securityId, predicate);
            return new CanonicalReadResponse<>(CanonicalDataState.AVAILABLE, "LATEST_OFFICIAL_MARKET_D",
                    securityId, "SECURITY", value.tradingDate(), value.tradingDate(), null, value);
        } catch (KrxCurrentExactMissException absent) {
            return new CanonicalReadResponse<>(CanonicalDataState.NO_DATA, "LATEST_OFFICIAL_MARKET_D",
                    securityId, "SECURITY", absent.tradingDate(), absent.tradingDate(), null, null);
        } catch (KrxCurrentAmbiguousException ambiguous) {
            return new CanonicalReadResponse<>(CanonicalDataState.BLOCKED, "LATEST_OFFICIAL_MARKET_D",
                    securityId, "SECURITY", null, null, null, null);
        } catch (IllegalArgumentException unsupported) {
            return new CanonicalReadResponse<>(CanonicalDataState.UNSUPPORTED, "LATEST_OFFICIAL_MARKET_D",
                    securityId, "SECURITY", null, null, null, null);
        } catch (IllegalStateException unavailable) {
            // No preceding target Fact is selected when the exact official D observation is absent.
            return new CanonicalReadResponse<>(CanonicalDataState.UNAVAILABLE, "LATEST_OFFICIAL_MARKET_D",
                    securityId, "SECURITY", null, null, null, null);
        }
    }
}
