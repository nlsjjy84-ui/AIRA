package com.aira.api.market.query;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.repository.FactRepository;
import com.aira.api.market.repository.MarketEntityRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicCompanyCatalogQuery {
    private static final Set<FactPredicate> SUPPORTED =
            Set.copyOf(EnumSet.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME));

    private final MarketEntityRepository entities;
    private final FactRepository facts;

    public PublicCompanyCatalogQuery(MarketEntityRepository entities, FactRepository facts) {
        this.entities = entities;
        this.facts = facts;
    }

    @Transactional(readOnly = true)
    public List<PublicCompanyView> findCompanies() {
        return entities.findPubliclyAvailableCompanies().stream()
                .map(entity -> new PublicCompanyView(
                        entity.getId(), entity.getCanonicalName(), entity.getCountryCode()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CompanyFinancialPeriodView> findPeriods(UUID companyId) {
        var company = entities.findById(companyId)
                .filter(entity -> entity.getEntityType() == EntityType.COMPANY && entity.isActive())
                .orElseThrow(() -> new PublicCompanyCatalogException(
                        PublicCompanyCatalogException.Category.COMPANY_NOT_FOUND,
                        "Company was not found"));
        var byPeriod = new TreeMap<PeriodKey, EnumSet<FactPredicate>>(
                Comparator.comparing(PeriodKey::periodEnd).reversed()
                        .thenComparing(PeriodKey::periodStart, Comparator.reverseOrder()));
        facts.findPubliclyAvailableFacts(company.getId()).forEach(fact -> {
            if (SUPPORTED.contains(fact.getPredicate())) {
                byPeriod.computeIfAbsent(
                        new PeriodKey(fact.getPeriodStart(), fact.getPeriodEnd()),
                        ignored -> EnumSet.noneOf(FactPredicate.class)).add(fact.getPredicate());
            }
        });
        if (byPeriod.isEmpty()) {
            throw new PublicCompanyCatalogException(
                    PublicCompanyCatalogException.Category.PERIODS_NOT_FOUND,
                    "No financial periods are available for this company");
        }
        return byPeriod.entrySet().stream()
                .map(entry -> new CompanyFinancialPeriodView(entry.getKey().periodStart(),
                        entry.getKey().periodEnd(), Set.copyOf(entry.getValue())))
                .toList();
    }

    private record PeriodKey(LocalDate periodStart, LocalDate periodEnd) {}
}
