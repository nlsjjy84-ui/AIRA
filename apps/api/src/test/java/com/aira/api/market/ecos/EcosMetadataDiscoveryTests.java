package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class EcosMetadataDiscoveryTests {
    @Test
    void plansOnlyExactRangesNeededByTotalCount() {
        RecordingClient client = new RecordingClient(2001);
        EcosMetadataDiscovery discovery = new EcosMetadataDiscovery(client, new ObjectMapper());

        EcosMetadataDiscoveryResult result = discovery.discover(
                "200Y104", new EcosDiscoveryBudget(2001, 3, 6));

        assertEquals(2001, result.totalCount());
        assertEquals(3, result.pageRequests());
        assertEquals(6, result.worstCaseHttpAttempts());
        assertEquals(2001, result.items().size());
        assertEquals(List.of(
                new EcosPageRange(1, 1000),
                new EcosPageRange(1001, 2000),
                new EcosPageRange(2001, 2001)), client.ranges());
    }
    @Test
    void budgetBlocksBeforeAnyAdditionalPageRequest() {
        RecordingClient client = new RecordingClient(1001);
        EcosMetadataDiscovery discovery = new EcosMetadataDiscovery(client, new ObjectMapper());

        assertThrows(EcosDiscoveryBudgetExceededException.class,
                () -> discovery.discover(
                        "200Y104", new EcosDiscoveryBudget(1000, 1, 2)));

        assertEquals(List.of(new EcosPageRange(1, 1000)), client.ranges());
    }

    @Test
    void singlePageUsesExactlyOneLogicalRequest() {
        RecordingClient client = new RecordingClient(53);
        EcosMetadataDiscovery discovery = new EcosMetadataDiscovery(client, new ObjectMapper());

        EcosMetadataDiscoveryResult result = discovery.discover(
                "200Y104", new EcosDiscoveryBudget(1000, 1, 2));

        assertEquals(53, result.totalCount());
        assertEquals(1, result.pageRequests());
        assertEquals(2, result.worstCaseHttpAttempts());
        assertEquals(List.of(new EcosPageRange(1, 1000)), client.ranges());
    }
    @Test
    void httpAttemptBudgetUsesWorstCaseRetryCeiling() {
        RecordingClient client = new RecordingClient(1001);
        EcosMetadataDiscovery discovery = new EcosMetadataDiscovery(client, new ObjectMapper());

        assertThrows(EcosDiscoveryBudgetExceededException.class,
                () -> discovery.discover(
                        "200Y104", new EcosDiscoveryBudget(1001, 2, 3)));

        assertEquals(List.of(new EcosPageRange(1, 1000)), client.ranges());
    }

    @Test
    void rejectsIncompleteFirstPageBeforePagination() {
        EcosMetadataClient client = request -> page(request, 1001, 1);
        EcosMetadataDiscovery discovery = new EcosMetadataDiscovery(client, new ObjectMapper());

        EcosProviderException error = assertThrows(EcosProviderException.class,
                () -> discovery.discover(
                        "200Y104", new EcosDiscoveryBudget(2000, 2, 4)));

        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, error.category());
    }

    @Test
    void budgetItselfMustAuthorizeMandatoryFirstPage() {
        assertThrows(IllegalArgumentException.class,
                () -> new EcosDiscoveryBudget(999, 1, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new EcosDiscoveryBudget(1000, 0, 2));
        assertThrows(IllegalArgumentException.class,
                () -> new EcosDiscoveryBudget(1000, 1, 1));
    }
    @Test
    void rejectsTotalCountChangingBetweenPages() {
        AtomicInteger calls = new AtomicInteger();
        EcosMetadataClient client = request -> {
            int call = calls.incrementAndGet();
            if (call == 1) return page(request, 1001, 1000);
            return page(request, 1002, 1);
        };
        EcosMetadataDiscovery discovery = new EcosMetadataDiscovery(client, new ObjectMapper());

        EcosProviderException error = assertThrows(EcosProviderException.class,
                () -> discovery.discover(
                        "200Y104", new EcosDiscoveryBudget(2000, 2, 4)));

        assertEquals(EcosProviderException.Category.MALFORMED_RESPONSE, error.category());
        assertEquals(2, calls.get());
    }

    private static EcosRawResponse page(
            EcosRequestDescriptor request, long totalCount, int rowCount) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < rowCount; i++) {
            if (i > 0) rows.append(',');
            long sequence = request.startRow() + i;
            rows.append(row(request.statCode(), sequence));
        }
        String body = "{\"StatisticItemList\":{\"list_total_count\":"
                + totalCount + ",\"row\":[" + rows + "]}}";
        return new EcosRawResponse(200, body);
    }
    private static String row(String statCode, long sequence) {
        return "{"
                + "\"STAT_CODE\":\"" + statCode + "\","
                + "\"STAT_NAME\":\"통계표\","
                + "\"GRP_CODE\":\"Group1\",\"GRP_NAME\":\"그룹\","
                + "\"ITEM_CODE\":\"I" + sequence + "\","
                + "\"ITEM_NAME\":\"항목" + sequence + "\","
                + "\"P_ITEM_CODE\":null,\"P_ITEM_NAME\":null,"
                + "\"CYCLE\":\"Q\",\"START_TIME\":\"1960Q1\","
                + "\"END_TIME\":\"2026Q2\",\"DATA_CNT\":266,"
                + "\"UNIT_NAME\":\"십억원\",\"WEIGHT\":null}";
    }

    private static final class RecordingClient implements EcosMetadataClient {
        private final long totalCount;
        private final List<EcosPageRange> ranges = new ArrayList<>();

        private RecordingClient(long totalCount) {
            this.totalCount = totalCount;
        }

        @Override
        public EcosRawResponse fetch(EcosRequestDescriptor request) {
            ranges.add(new EcosPageRange(request.startRow(), request.endRow()));
            long remaining = Math.max(0, totalCount - request.startRow() + 1);
            int rowCount = (int) Math.min(
                    request.endRow() - request.startRow() + 1, remaining);
            return page(request, totalCount, rowCount);
        }

        private List<EcosPageRange> ranges() {
            return List.copyOf(ranges);
        }
    }
}
