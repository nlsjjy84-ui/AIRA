package com.aira.api.delivery.service;

import com.aira.api.delivery.dto.RelatedCompany;
import java.util.Comparator;
import java.util.List;

final class RelatedCompanyOrder {
    private RelatedCompanyOrder() {}

    static List<RelatedCompany> normalize(List<RelatedCompany> companies) {
        return companies.stream().distinct()
                .sorted(Comparator.comparing(RelatedCompany::companyId))
                .toList();
    }
}
