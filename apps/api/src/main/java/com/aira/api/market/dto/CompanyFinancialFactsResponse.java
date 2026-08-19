package com.aira.api.market.dto;

import java.util.List;
import java.util.UUID;

public record CompanyFinancialFactsResponse(
        UUID companyId,
        List<CompanyFinancialFactResponse> facts) {

    public CompanyFinancialFactsResponse {
        facts = List.copyOf(facts);
    }
}
