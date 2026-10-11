package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.service.EntityExternalIdentifierRegistryService;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class OfficialAllCompaniesPreparationOperationTests {
    private final OfficialCompanyDataPreparationOperation companyData =
            mock(OfficialCompanyDataPreparationOperation.class);
    private final EntityExternalIdentifierRegistryService identifiers =
            mock(EntityExternalIdentifierRegistryService.class);
    private final OfficialAllCompaniesPreparationOperation operation =
            new OfficialAllCompaniesPreparationOperation(companyData, identifiers);

    private static OpenDartCompanyDirectoryRecord record(String corpCode, String stockCode) {
        return new OpenDartCompanyDirectoryRecord(corpCode, "회사" + corpCode, null, stockCode,
                LocalDate.of(2026, 1, 1));
    }

    private OpenDartCompanyDirectory directory() {
        var directory = new OpenDartCompanyDirectory(List.of(
                record("00000001", "000001"), record("00000002", "000002"), record("00000003", "000003"),
                record("00000004", "000004"), record("00000005", null), record("00000006", "000006")));
        when(companyData.fetchDirectory()).thenReturn(directory);
        when(identifiers.findEntity(any())).thenReturn(Optional.empty());
        return directory;
    }

    @Test
    void preparesOnlyListedCompaniesThatAreNotStoredYetAndIsolatesEachCompanysFailure() {
        var directory = directory();
        when(identifiers.findEntity(any())).thenAnswer(invocation -> {
            var key = (com.aira.api.market.service.ExternalIdentifierKey) invocation.getArgument(0);
            return "00000001".equals(key.identifierValue()) ? Optional.of(mock(MarketEntity.class)) : Optional.empty();
        });
        when(companyData.prepareOne(directory, "000003", 2025)).thenThrow(new OpenDartProviderException(
                OpenDartProviderException.Category.NO_DATA, "no consolidated statements"));
        when(companyData.prepareOne(directory, "000004", 2025)).thenThrow(new IllegalStateException("boom"));
        var skipped = new HashSet<String>();

        var result = operation.run(2025, 100, 0, skipped);

        assertEquals(4, result.pendingBefore());
        assertEquals(2, result.prepared());
        assertEquals(1, result.noData());
        assertEquals(1, result.failed());
        assertEquals(OfficialAllCompaniesPreparationOperation.StopReason.NONE, result.stopReason());
        assertEquals(java.util.Set.of("000003", "000004"), skipped);
        verify(companyData, never()).prepareOne(directory, "000001", 2025);
    }

    @Test
    void stopsAtTheBatchLimitAndReportsWhatRemains() {
        directory();
        var result = operation.run(2025, 2, 0, new HashSet<>());
        assertEquals(2, result.attempted());
        assertEquals(3, result.remaining());
    }

    @Test
    void stopsImmediatelyWhenTheDailyLimitIsReached() {
        directory();
        when(companyData.prepareOne(any(), any(), anyInt())).thenThrow(new OpenDartProviderException(
                OpenDartProviderException.Category.RATE_LIMIT, "020"));
        var result = operation.run(2025, 100, 0, new HashSet<>());
        assertEquals(1, result.attempted());
        assertEquals(OfficialAllCompaniesPreparationOperation.StopReason.RATE_LIMIT, result.stopReason());
    }

    @Test
    void stopsAfterFiveFailuresInARow() {
        directory();
        when(companyData.prepareOne(any(), any(), anyInt())).thenThrow(new IllegalStateException("x"));
        var result = operation.run(2025, 100, 0, new HashSet<>());
        assertEquals(5, result.attempted());
        assertEquals(OfficialAllCompaniesPreparationOperation.StopReason.REPEATED_FAILURES, result.stopReason());
    }

    @Test
    void doesNotRetryCompaniesAlreadyMarkedAsSkippedInThisRun() {
        var directory = directory();
        var skipped = new HashSet<>(List.of("000001", "000002", "000003", "000004", "000006"));
        var result = operation.run(2025, 100, 0, skipped);
        assertEquals(0, result.attempted());
        verify(companyData, never()).prepareOne(eq(directory), any(), anyInt());
    }

    @Test
    void rejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> operation.run(1999, 10, 0, new HashSet<>()));
        assertThrows(IllegalArgumentException.class, () -> operation.run(2025, 0, 0, new HashSet<>()));
        assertTrue(true);
    }
}
