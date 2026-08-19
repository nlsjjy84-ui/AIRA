package com.aira.api.market.controller;

import com.aira.api.market.dto.CompanyFinancialPeriodResponse;
import com.aira.api.market.dto.CompanyFinancialPeriodsResponse;
import com.aira.api.market.dto.PublicCompaniesResponse;
import com.aira.api.market.dto.PublicCompanyResponse;
import com.aira.api.market.query.PublicCompanyCatalogQuery;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies")
public class PublicCompanyCatalogController {
    private final PublicCompanyCatalogQuery query;

    public PublicCompanyCatalogController(PublicCompanyCatalogQuery query) {
        this.query = query;
    }

    @GetMapping
    public PublicCompaniesResponse findCompanies() {
        return new PublicCompaniesResponse(query.findCompanies().stream()
                .map(company -> new PublicCompanyResponse(company.companyId(),
                        company.canonicalName(), company.countryCode()))
                .toList());
    }

    @GetMapping("/{companyId}/financial-periods")
    public CompanyFinancialPeriodsResponse findPeriods(@PathVariable UUID companyId) {
        return new CompanyFinancialPeriodsResponse(companyId, query.findPeriods(companyId).stream()
                .map(period -> new CompanyFinancialPeriodResponse(period.periodStart(),
                        period.periodEnd(), period.predicates()))
                .toList());
    }
}
