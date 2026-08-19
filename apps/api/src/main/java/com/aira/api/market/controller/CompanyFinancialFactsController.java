package com.aira.api.market.controller;

import com.aira.api.market.dto.CompanyFinancialFactResponse;
import com.aira.api.market.dto.CompanyFinancialFactsResponse;
import com.aira.api.market.query.CompanyFinancialFactsQuery;
import com.aira.api.market.query.CompanyFinancialFactsQueryInput;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/{companyId}/financial-facts")
public class CompanyFinancialFactsController {
    private final CompanyFinancialFactsQuery query;

    public CompanyFinancialFactsController(CompanyFinancialFactsQuery query) {
        this.query = query;
    }

    @GetMapping
    public CompanyFinancialFactsResponse find(
            @PathVariable UUID companyId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
            @RequestParam(required = false) Set<String> predicates) {
        var result = query.find(new CompanyFinancialFactsQueryInput(
                companyId, predicates, periodStart, periodEnd));
        var facts = result.facts().stream().map(view -> new CompanyFinancialFactResponse(
                view.companyId(), view.predicate(), view.value(), view.currency(),
                view.periodStart(), view.periodEnd(), view.publishedAt(), view.collectedAt(),
                view.sourceName(), view.evidenceId(), view.evidenceExternalId(),
                view.evidenceOriginalUrl())).toList();
        return new CompanyFinancialFactsResponse(result.companyId(), facts);
    }
}
