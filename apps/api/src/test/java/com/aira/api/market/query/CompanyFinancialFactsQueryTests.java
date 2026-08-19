package com.aira.api.market.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.Evidence;
import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.FactAssertion;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.FactStatus;
import com.aira.api.market.domain.FactValueType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.domain.Source;
import com.aira.api.market.repository.FactAssertionRepository;
import com.aira.api.market.repository.FactRepository;
import com.aira.api.market.repository.MarketEntityRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyFinancialFactsQueryTests {
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private static final UUID EVIDENCE_ID = UUID.randomUUID();
    private static final LocalDate START = LocalDate.of(2025, 1, 1);
    private static final LocalDate END = LocalDate.of(2025, 12, 31);
    private static final OffsetDateTime COLLECTED =
            OffsetDateTime.parse("2026-08-19T07:00:00Z");

    @Mock private MarketEntityRepository entities;
    @Mock private FactRepository facts;
    @Mock private FactAssertionRepository assertions;
    private CompanyFinancialFactsQuery query;
    private MarketEntity company;

    @BeforeEach
    void setUp() throws Exception {
        query = new CompanyFinancialFactsQuery(entities, facts, assertions);
        company = instance(MarketEntity.class);
        set(company, "id", COMPANY_ID);
        set(company, "entityType", EntityType.COMPANY);
        when(entities.findById(COMPANY_ID)).thenReturn(Optional.of(company));
    }

    @Test
    void readsExactPeriodFactsWithPreciseValuesAndSharedProvenance() throws Exception {
        Fact revenue = fact(FactPredicate.REVENUE, "333605938000000");
        Fact operatingIncome = fact(FactPredicate.OPERATING_INCOME, "43601051000000");
        Evidence evidence = evidence(null);
        when(facts.findBySubjectEntityIdAndPredicateInAndPeriodStartAndPeriodEnd(
                COMPANY_ID, Set.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME),
                START, END)).thenReturn(List.of(revenue, operatingIncome));
        when(assertions.findWithProvenanceByFactIds(
                Set.of(revenue.getId(), operatingIncome.getId())))
                .thenReturn(List.of(assertion(revenue, evidence), assertion(operatingIncome, evidence)));

        CompanyFinancialFactsResult result = query.find(input(Set.of()));

        assertEquals(COMPANY_ID, result.companyId());
        assertEquals(2, result.facts().size());
        var revenueView = result.facts().stream()
                .filter(view -> view.predicate() == FactPredicate.REVENUE).findFirst().orElseThrow();
        var operatingView = result.facts().stream()
                .filter(view -> view.predicate() == FactPredicate.OPERATING_INCOME)
                .findFirst().orElseThrow();
        assertEquals(new BigDecimal("333605938000000"), revenueView.value());
        assertEquals(new BigDecimal("43601051000000"), operatingView.value());
        assertEquals("KRW", revenueView.currency());
        assertEquals(START, revenueView.periodStart());
        assertEquals(END, revenueView.periodEnd());
        assertNull(revenueView.publishedAt());
        assertEquals(COLLECTED, revenueView.collectedAt());
        assertEquals("OpenDART", revenueView.sourceName());
        assertEquals("20260310002820", revenueView.evidenceExternalId());
        assertEquals("https://dart.example/filing", revenueView.evidenceOriginalUrl());
        assertEquals(revenueView.evidenceId(), operatingView.evidenceId());
    }

    @Test
    void preservesPublishedAndCollectedTimestampsSeparately() throws Exception {
        OffsetDateTime published = OffsetDateTime.parse("2026-03-10T01:00:00Z");
        Fact fact = fact(FactPredicate.REVENUE, "1.00");
        Evidence evidence = evidence(published);
        when(facts.findBySubjectEntityIdAndPredicateInAndPeriodStartAndPeriodEnd(
                COMPANY_ID, Set.of(FactPredicate.REVENUE), START, END)).thenReturn(List.of(fact));
        when(assertions.findWithProvenanceByFactIds(Set.of(fact.getId())))
                .thenReturn(List.of(assertion(fact, evidence)));

        var view = query.find(input(Set.of("REVENUE"))).facts().getFirst();

        assertEquals(published, view.publishedAt());
        assertEquals(COLLECTED, view.collectedAt());
    }

    @Test
    void distinguishesMissingCompany() {
        when(entities.findById(COMPANY_ID)).thenReturn(Optional.empty());

        var failure = assertThrows(CompanyFinancialFactsQueryException.class,
                () -> query.find(input(Set.of())));

        assertEquals(CompanyFinancialFactsQueryException.Category.COMPANY_NOT_FOUND,
                failure.category());
        verify(facts, never()).findBySubjectEntityIdAndPredicateInAndPeriodStartAndPeriodEnd(
                any(), any(), any(), any());
    }

    @Test
    void distinguishesMissingExactPeriodFacts() {
        when(facts.findBySubjectEntityIdAndPredicateInAndPeriodStartAndPeriodEnd(
                COMPANY_ID, Set.of(FactPredicate.REVENUE), START, END)).thenReturn(List.of());

        var failure = assertThrows(CompanyFinancialFactsQueryException.class,
                () -> query.find(input(Set.of("REVENUE"))));

        assertEquals(CompanyFinancialFactsQueryException.Category.FACTS_NOT_FOUND,
                failure.category());
    }

    @Test
    void rejectsUnsupportedPredicateBeforeFactQuery() {
        var failure = assertThrows(CompanyFinancialFactsQueryException.class,
                () -> query.find(input(Set.of("NET_INCOME"))));

        assertEquals(CompanyFinancialFactsQueryException.Category.UNSUPPORTED_PREDICATE,
                failure.category());
        verify(facts, never()).findBySubjectEntityIdAndPredicateInAndPeriodStartAndPeriodEnd(
                any(), any(), any(), any());
    }

    @Test
    void rejectsMissingOrInconsistentProvenance() throws Exception {
        Fact fact = fact(FactPredicate.REVENUE, "1");
        when(facts.findBySubjectEntityIdAndPredicateInAndPeriodStartAndPeriodEnd(
                COMPANY_ID, Set.of(FactPredicate.REVENUE), START, END)).thenReturn(List.of(fact));
        when(assertions.findWithProvenanceByFactIds(Set.of(fact.getId())))
                .thenReturn(List.of());

        var failure = assertThrows(CompanyFinancialFactsQueryException.class,
                () -> query.find(input(Set.of("REVENUE"))));

        assertEquals(CompanyFinancialFactsQueryException.Category.INCONSISTENT_PROVENANCE,
                failure.category());
    }

    private CompanyFinancialFactsQueryInput input(Set<String> predicates) {
        return new CompanyFinancialFactsQueryInput(COMPANY_ID, predicates, START, END);
    }

    private Fact fact(FactPredicate predicate, String value) throws Exception {
        Fact fact = instance(Fact.class);
        set(fact, "id", UUID.randomUUID());
        set(fact, "subjectEntity", company);
        set(fact, "predicate", predicate);
        set(fact, "status", FactStatus.SUPPORTED);
        set(fact, "valueType", FactValueType.NUMBER);
        set(fact, "valueNumber", new BigDecimal(value));
        set(fact, "currencyCode", "KRW");
        set(fact, "periodStart", START);
        set(fact, "periodEnd", END);
        return fact;
    }

    private Evidence evidence(OffsetDateTime published) throws Exception {
        Source source = instance(Source.class);
        set(source, "id", UUID.randomUUID());
        set(source, "name", "OpenDART");
        Evidence evidence = instance(Evidence.class);
        set(evidence, "id", EVIDENCE_ID);
        set(evidence, "source", source);
        set(evidence, "externalId", "20260310002820");
        set(evidence, "originalUrl", "https://dart.example/filing");
        set(evidence, "publishedAt", published);
        set(evidence, "collectedAt", COLLECTED);
        return evidence;
    }

    private FactAssertion assertion(Fact fact, Evidence evidence) throws Exception {
        FactAssertion assertion = instance(FactAssertion.class);
        set(assertion, "fact", fact);
        set(assertion, "evidence", evidence);
        return assertion;
    }

    private static <T> T instance(Class<T> type) throws Exception {
        var constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
