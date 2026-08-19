package com.aira.api.market.dto;

import java.util.List;

public record PublicCompaniesResponse(List<PublicCompanyResponse> companies) {
    public PublicCompaniesResponse {
        companies = List.copyOf(companies);
    }
}
