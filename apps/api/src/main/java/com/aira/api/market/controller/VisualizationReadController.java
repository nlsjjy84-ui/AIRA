package com.aira.api.market.controller;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.dto.FinancialExactComparisonResponse;
import com.aira.api.market.dto.KrxPreviousObservationResponse;
import com.aira.api.market.dto.KrxStoredSeriesResponse;
import com.aira.api.market.query.FinancialExactComparisonQuery;
import com.aira.api.market.query.KrxStoredSeriesQuery;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class VisualizationReadController {
    private final FinancialExactComparisonQuery financial;
    private final KrxStoredSeriesQuery market;
    public VisualizationReadController(FinancialExactComparisonQuery financial, KrxStoredSeriesQuery market) {
        this.financial = financial; this.market = market;
    }

    @GetMapping("/api/companies/{companyId}/financial-facts/compare")
    public FinancialExactComparisonResponse financial(@PathVariable UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate aStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate aEnd,
            @RequestParam String aReceipt,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate bEnd,
            @RequestParam String bReceipt,
            @RequestParam(required = false) Set<FactPredicate> predicates) {
        return financial.find(companyId,
                new FinancialExactComparisonResponse.ExactPeriod(aStart, aEnd, aReceipt),
                new FinancialExactComparisonResponse.ExactPeriod(bStart, bEnd, bReceipt),
                predicates == null || predicates.isEmpty()
                        ? Set.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME) : predicates);
    }

    @GetMapping("/api/securities/{securityId}/market-series")
    public KrxStoredSeriesResponse series(@PathVariable UUID securityId,
            @RequestParam FactPredicate predicate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return market.find(securityId, predicate, from, to);
    }

    @GetMapping("/api/securities/{securityId}/market-previous")
    public KrxPreviousObservationResponse previous(@PathVariable UUID securityId,
            @RequestParam FactPredicate predicate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate currentDate,
            @RequestParam UUID currentFactId) {
        return market.previous(securityId, predicate, currentDate, currentFactId);
    }
}
