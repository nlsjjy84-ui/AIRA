package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class EcosRealGdpObservationReaderTests {
    @Test
    void twoQuarterScopeRequestsOnlyTwoRows() {
        var requests = new ArrayList<EcosRealGdpSearchRequest>();
        EcosRealGdpStatisticSearchClient client = request -> {
            requests.add(request);
            return new EcosStatisticSearchPage(2, List.of(
                    observation("2026Q1", "596692.8"),
                    observation("2026Q2", "600474.9")));
        };
        var reader = new EcosRealGdpObservationReader(client);
        var scope = new EcosRealGdpObservationScope(
                "2026Q1", "2026Q2", new EcosObservationBudget(2, 1, 2));
        EcosRealGdpObservationResult result = reader.read(scope);

        assertEquals(1, requests.size());
        assertEquals(1, requests.getFirst().startRow());
        assertEquals(2, requests.getFirst().endRow());
        assertEquals("2026Q1", requests.getFirst().startTime());
        assertEquals("2026Q2", requests.getFirst().endTime());
        assertEquals(2, result.requestedQuarters());
        assertEquals(2, result.totalCount());
        assertEquals(1, result.pageRequests());
        assertEquals(2, result.worstCaseHttpAttempts());
    }

    @Test
    void budgetFailureBlocksBeforeAnyClientCall() {
        AtomicInteger calls = new AtomicInteger();
        EcosRealGdpStatisticSearchClient client = request -> {
            calls.incrementAndGet();
            throw new AssertionError("client must not be called");
        };
        var reader = new EcosRealGdpObservationReader(client);

        assertThrows(EcosObservationBudgetExceededException.class,
                () -> reader.read(new EcosRealGdpObservationScope(
                        "2026Q1", "2026Q2", new EcosObservationBudget(1, 1, 2))));
        assertEquals(0, calls.get());
    }
    @Test
    void pageAndHttpBudgetsBlockBeforeAnyCall() {
        AtomicInteger calls = new AtomicInteger();
        EcosRealGdpStatisticSearchClient client = request -> {
            calls.incrementAndGet();
            throw new AssertionError("client must not be called");
        };
        var reader = new EcosRealGdpObservationReader(client);

        assertThrows(EcosObservationBudgetExceededException.class,
                () -> reader.read(new EcosRealGdpObservationScope(
                        "2000Q1", "2250Q1", new EcosObservationBudget(1001, 1, 4))));
        assertThrows(EcosObservationBudgetExceededException.class,
                () -> reader.read(new EcosRealGdpObservationScope(
                        "2000Q1", "2250Q1", new EcosObservationBudget(1001, 2, 3))));
        assertEquals(0, calls.get());
    }

    @Test
    void oneThousandAndOneQuartersUseOnlyTwoExactPages() {
        var requests = new ArrayList<EcosRealGdpSearchRequest>();
        EcosRealGdpStatisticSearchClient client = request -> {
            requests.add(request);
            return page(request, 1001, "2000Q1");
        };
        var reader = new EcosRealGdpObservationReader(client);
        var result = reader.read(new EcosRealGdpObservationScope(
                "2000Q1", "2250Q1", new EcosObservationBudget(1001, 2, 4)));

        assertEquals(2, requests.size());
        assertEquals(new EcosPageRange(1, 1000),
                new EcosPageRange(requests.get(0).startRow(), requests.get(0).endRow()));
        assertEquals(new EcosPageRange(1001, 1001),
                new EcosPageRange(requests.get(1).startRow(), requests.get(1).endRow()));
        assertEquals(1001, result.observations().size());
        assertEquals(2, result.pageRequests());
        assertEquals(4, result.worstCaseHttpAttempts());
    }

    @Test
    void providerCannotExpandBeyondRequestedQuarterScope() {
        AtomicInteger calls = new AtomicInteger();
        EcosRealGdpStatisticSearchClient client = request -> {
            calls.incrementAndGet();
            return new EcosStatisticSearchPage(3, List.of(
                    observation("2026Q1", "1"), observation("2026Q2", "2")));
        };
        var reader = new EcosRealGdpObservationReader(client);
        var error = assertThrows(EcosProviderException.class,
                () -> reader.read(new EcosRealGdpObservationScope(
                        "2026Q1", "2026Q2", new EcosObservationBudget(2, 1, 2))));

        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, error.category());
        assertEquals(1, calls.get());
    }

    @Test
    void outOfScopeTimeIsBlocked() {
        EcosRealGdpStatisticSearchClient client = request ->
                new EcosStatisticSearchPage(1, List.of(observation("2025Q4", "1")));
        var reader = new EcosRealGdpObservationReader(client);

        var error = assertThrows(EcosProviderException.class,
                () -> reader.read(new EcosRealGdpObservationScope(
                        "2026Q1", "2026Q1", new EcosObservationBudget(1, 1, 2))));

        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, error.category());
    }

    @Test
    void lowLevelRequestTypesAreNotPublicApi() {
        assertFalse(Modifier.isPublic(EcosRealGdpSearchRequest.class.getModifiers()));
        assertFalse(Modifier.isPublic(EcosRealGdpStatisticSearchClient.class.getModifiers()));
    }
    private static EcosStatisticSearchPage page(
            EcosRealGdpSearchRequest request, long totalCount, String firstQuarter) {
        var observations = new ArrayList<EcosStatisticSearchObservation>();
        long finalRow = Math.min(request.endRow(), totalCount);
        for (long row = request.startRow(); row <= finalRow; row++) {
            String time = quarterAt(firstQuarter, row - 1L);
            observations.add(observation(time, Long.toString(row)));
        }
        return new EcosStatisticSearchPage(totalCount, observations);
    }

    private static String quarterAt(String firstQuarter, long offset) {
        long year = Long.parseLong(firstQuarter.substring(0, 4));
        long quarter = firstQuarter.charAt(5) - '0';
        long ordinal = year * 4L + quarter - 1L + offset;
        long targetYear = ordinal / 4L;
        long targetQuarter = ordinal % 4L + 1L;
        return String.format("%04dQ%d", targetYear, targetQuarter);
    }
    private static EcosStatisticSearchObservation observation(String time, String value) {
        return new EcosStatisticSearchObservation(
                EcosRealGdpContract.STAT_CODE,
                EcosRealGdpContract.STAT_NAME,
                EcosRealGdpContract.ITEM_CODE1,
                EcosRealGdpContract.ITEM_NAME1,
                null, null, null, null, null, null,
                EcosRealGdpContract.UNIT_NAME,
                null,
                time,
                value,
                new BigDecimal(value));
    }


    @Test
    void changingTotalCountBetweenPagesIsBlocked() {
        AtomicInteger calls = new AtomicInteger();
        EcosRealGdpStatisticSearchClient client = request -> {
            int call = calls.incrementAndGet();
            if (call == 1) return page(request, 1001, "2000Q1");
            return new EcosStatisticSearchPage(1000, List.of());
        };
        var reader = new EcosRealGdpObservationReader(client);
        var error = assertThrows(EcosProviderException.class,
                () -> reader.read(new EcosRealGdpObservationScope(
                        "2000Q1", "2250Q1", new EcosObservationBudget(1001, 2, 4))));
        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, error.category());
        assertEquals(2, calls.get());
    }
}
