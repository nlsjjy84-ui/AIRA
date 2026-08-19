package com.aira.api.market.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.Fact;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.FactRepository;
import com.aira.api.market.repository.MarketEntityRepository;
import java.lang.reflect.Field;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PublicCompanyCatalogQueryTests {
    private static final UUID COMPANY_ID = UUID.randomUUID();
    private final MarketEntityRepository entities = mock(MarketEntityRepository.class);
    private final FactRepository facts = mock(FactRepository.class);
    private final PublicCompanyCatalogQuery query = new PublicCompanyCatalogQuery(entities, facts);

    @Test
    void returnsOnlyRepositoryQualifiedCompanies() throws Exception {
        MarketEntity company = company();
        when(entities.findPubliclyAvailableCompanies()).thenReturn(List.of(company));
        assertEquals(List.of(new PublicCompanyView(COMPANY_ID, "삼성전자", "KR")),
                query.findCompanies());
    }

    @Test
    void groupsPredicatesByExactPeriodAndOrdersPeriodsDescending() throws Exception {
        MarketEntity company = company();
        when(entities.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(facts.findPubliclyAvailableFacts(COMPANY_ID)).thenReturn(List.of(
                fact(company, FactPredicate.REVENUE, LocalDate.of(2024, 1, 1),
                        LocalDate.of(2024, 12, 31)),
                fact(company, FactPredicate.REVENUE, LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31)),
                fact(company, FactPredicate.OPERATING_INCOME, LocalDate.of(2025, 1, 1),
                        LocalDate.of(2025, 12, 31))));

        var periods = query.findPeriods(COMPANY_ID);
        assertEquals(LocalDate.of(2025, 12, 31), periods.getFirst().periodEnd());
        assertEquals(2, periods.getFirst().predicates().size());
        assertEquals(LocalDate.of(2024, 12, 31), periods.get(1).periodEnd());
    }

    @Test
    void rejectsMissingCompanyAndCompanyWithoutPeriods() throws Exception {
        when(entities.findById(COMPANY_ID)).thenReturn(Optional.empty());
        assertEquals(PublicCompanyCatalogException.Category.COMPANY_NOT_FOUND,
                assertThrows(PublicCompanyCatalogException.class,
                        () -> query.findPeriods(COMPANY_ID)).category());

        MarketEntity company = company();
        when(entities.findById(COMPANY_ID)).thenReturn(Optional.of(company));
        when(facts.findPubliclyAvailableFacts(COMPANY_ID)).thenReturn(List.of());
        assertEquals(PublicCompanyCatalogException.Category.PERIODS_NOT_FOUND,
                assertThrows(PublicCompanyCatalogException.class,
                        () -> query.findPeriods(COMPANY_ID)).category());
    }

    private static MarketEntity company() throws Exception {
        MarketEntity company = MarketEntity.company("삼성전자", "KR", UUID.randomUUID(),
                OffsetDateTime.now(ZoneOffset.UTC));
        set(company, "id", COMPANY_ID);
        return company;
    }

    private static Fact fact(MarketEntity company, FactPredicate predicate,
            LocalDate start, LocalDate end) {
        return Fact.supportedNumber(company, null, predicate, java.math.BigDecimal.ONE, "KRW",
                start, end, new byte[32], OffsetDateTime.now(ZoneOffset.UTC));
    }

    private static void set(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
