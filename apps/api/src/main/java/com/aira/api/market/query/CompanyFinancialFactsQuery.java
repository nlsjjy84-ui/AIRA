package com.aira.api.market.query;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.FactAssertion;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.FactStatus;
import com.aira.api.market.repository.FactAssertionRepository;
import com.aira.api.market.repository.FactRepository;
import com.aira.api.market.repository.MarketEntityRepository;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyFinancialFactsQuery {
    private static final Set<FactPredicate> SUPPORTED =
            Set.copyOf(EnumSet.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME));

    private final MarketEntityRepository entities;
    private final FactRepository facts;
    private final FactAssertionRepository assertions;

    public CompanyFinancialFactsQuery(MarketEntityRepository entities, FactRepository facts,
            FactAssertionRepository assertions) {
        this.entities = entities;
        this.facts = facts;
        this.assertions = assertions;
    }

    @Transactional(readOnly = true)
    public CompanyFinancialFactsResult find(CompanyFinancialFactsQueryInput input) {
        if (input == null) {
            throw new IllegalArgumentException("Company financial facts query is required");
        }
        var company = entities.findById(input.companyId())
                .filter(entity -> entity.getEntityType() == EntityType.COMPANY)
                .orElseThrow(() -> failure(
                        CompanyFinancialFactsQueryException.Category.COMPANY_NOT_FOUND,
                        "Company was not found"));
        Set<FactPredicate> predicates = predicates(input.predicates());
        List<Fact> matchedFacts = facts
                .findBySubjectEntityIdAndPredicateInAndPeriodStartAndPeriodEnd(
                        company.getId(), predicates, input.periodStart(), input.periodEnd());
        if (matchedFacts.isEmpty()) {
            throw failure(CompanyFinancialFactsQueryException.Category.FACTS_NOT_FOUND,
                    "No financial facts exist for the exact reporting period");
        }
        if (matchedFacts.stream().anyMatch(fact -> fact.getStatus() != FactStatus.SUPPORTED
                || fact.getValueNumber() == null)) {
            throw inconsistent();
        }

        Set<UUID> factIds = new HashSet<>();
        matchedFacts.forEach(fact -> factIds.add(fact.getId()));
        List<FactAssertion> matchedAssertions = assertions.findWithProvenanceByFactIds(factIds);
        Set<UUID> assertedFactIds = new HashSet<>();
        matchedAssertions.forEach(assertion -> assertedFactIds.add(assertion.getFact().getId()));
        if (!assertedFactIds.equals(factIds)) {
            throw inconsistent();
        }

        List<CompanyFinancialFactView> views = matchedAssertions.stream()
                .map(this::view)
                .sorted(Comparator.comparing((CompanyFinancialFactView view) ->
                                view.predicate().name())
                        .thenComparing(CompanyFinancialFactView::currency)
                        .thenComparing(view -> view.evidenceId().toString()))
                .toList();
        return new CompanyFinancialFactsResult(company.getId(), views);
    }

    private Set<FactPredicate> predicates(Set<String> requested) {
        if (requested.isEmpty()) {
            return SUPPORTED;
        }
        var parsed = EnumSet.noneOf(FactPredicate.class);
        for (String value : requested) {
            try {
                FactPredicate predicate = FactPredicate.valueOf(value);
                if (!SUPPORTED.contains(predicate)) {
                    throw new IllegalArgumentException();
                }
                parsed.add(predicate);
            } catch (IllegalArgumentException | NullPointerException exception) {
                throw failure(
                        CompanyFinancialFactsQueryException.Category.UNSUPPORTED_PREDICATE,
                        "Unsupported company financial predicate");
            }
        }
        return Set.copyOf(parsed);
    }

    private CompanyFinancialFactView view(FactAssertion assertion) {
        Fact fact = assertion.getFact();
        var evidence = assertion.getEvidence();
        var source = evidence.getSource();
        if (source == null || source.getId() == null || source.getName() == null
                || source.getName().isBlank() || evidence.getId() == null
                || evidence.getExternalId() == null || evidence.getExternalId().isBlank()
                || evidence.getOriginalUrl() == null || evidence.getOriginalUrl().isBlank()
                || evidence.getCollectedAt() == null) {
            throw inconsistent();
        }
        return new CompanyFinancialFactView(fact.getSubjectEntity().getId(), fact.getPredicate(),
                fact.getValueNumber(), fact.getCurrencyCode(), fact.getPeriodStart(),
                fact.getPeriodEnd(), evidence.getPublishedAt(), evidence.getCollectedAt(),
                source.getName(), evidence.getId(), evidence.getExternalId(),
                evidence.getOriginalUrl());
    }

    private static CompanyFinancialFactsQueryException inconsistent() {
        return failure(CompanyFinancialFactsQueryException.Category.INCONSISTENT_PROVENANCE,
                "Financial fact provenance is missing or inconsistent");
    }

    private static CompanyFinancialFactsQueryException failure(
            CompanyFinancialFactsQueryException.Category category, String message) {
        return new CompanyFinancialFactsQueryException(category, message);
    }
}
