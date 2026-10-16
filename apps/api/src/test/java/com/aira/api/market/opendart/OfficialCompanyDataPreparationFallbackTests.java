package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.service.EntityAliasRegistryService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 연결재무제표가 아예 없는 회사만 별도재무제표(OFS)로 받는다. */
class OfficialCompanyDataPreparationFallbackTests {
    private final OpenDartAnnualCfsAdapter annual = mock(OpenDartAnnualCfsAdapter.class);
    private final OpenDartFilingPersistence persistence = mock(OpenDartFilingPersistence.class);
    private final OpenDartCompanyProfileClient profiles = mock(OpenDartCompanyProfileClient.class);
    private final OfficialCompanyDataPreparationOperation operation = new OfficialCompanyDataPreparationOperation(
            mock(OpenDartCompanyDirectoryClient.class), profiles, persistence, annual, mock(EntityAliasRegistryService.class));
    private final OpenDartCompanyDirectory directory = new OpenDartCompanyDirectory(List.of(
            new OpenDartCompanyDirectoryRecord("00112233", "작은회사", null, "123450", LocalDate.of(2026, 1, 1))));

    private static boolean division(OpenDartAnnualCfsContext context, String fs) {
        return context != null && fs.equals(context.financialStatementDivision());
    }

    @Test
    void aCompanyWithoutConsolidatedStatementsIsCollectedFromSeparateStatements() {
        when(profiles.fetch("00112233")).thenReturn(new OpenDartCompanyProfile("00112233", "작은회사", 12));
        when(annual.prepare(argThat(c -> division(c, "CFS")), any(List.class))).thenThrow(
                new OpenDartProviderException(OpenDartProviderException.Category.NO_DATA, "OpenDART request failed with status 013"));
        var filing = mock(OpenDartPreparedFiling.class);
        when(annual.prepare(argThat(c -> division(c, "OFS")), any(List.class))).thenReturn(filing);
        when(persistence.persist(any(), any())).thenReturn(new OpenDartFilingPersistence.Saved(UUID.randomUUID(), List.of()));

        operation.prepareOne(directory, "123450", 2025);

        verify(annual).prepare(argThat(c -> division(c, "OFS")), argThat((List<FactPredicate> p) -> p.equals(FactPredicate.COLLECTED_COMPANY_FINANCIALS)));
    }

    @Test
    void aConsolidatedFilingWithoutSupportedAccountsIsNotSwappedForSeparateOnes() {
        when(profiles.fetch("00112233")).thenReturn(new OpenDartCompanyProfile("00112233", "작은회사", 12));
        when(annual.prepare(argThat(c -> division(c, "CFS")), any(List.class))).thenThrow(
                new OpenDartProviderException(OpenDartProviderException.Category.NO_DATA, "Supported annual CFS account was not returned"));

        var failure = assertThrows(OpenDartProviderException.class, () -> operation.prepareOne(directory, "123450", 2025));

        assertEquals(OpenDartProviderException.Category.NO_DATA, failure.category());
        verify(annual, never()).prepare(argThat(c -> division(c, "OFS")), any(List.class));
    }
}
