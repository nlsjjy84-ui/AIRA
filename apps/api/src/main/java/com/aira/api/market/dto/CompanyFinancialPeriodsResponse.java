package com.aira.api.market.dto;

import java.util.List;
import java.util.UUID;

public record CompanyFinancialPeriodsResponse(
        UUID companyId, List<CompanyFinancialPeriodResponse> periods) {
    public CompanyFinancialPeriodsResponse {
        periods = List.copyOf(periods);
    }
}
