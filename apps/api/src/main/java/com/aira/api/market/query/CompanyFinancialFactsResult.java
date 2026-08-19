package com.aira.api.market.query;

import java.util.List;
import java.util.UUID;

public record CompanyFinancialFactsResult(
        UUID companyId,
        List<CompanyFinancialFactView> facts) {

    public CompanyFinancialFactsResult {
        facts = List.copyOf(facts);
    }
}
