package com.aira.api.market.ecos;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class EcosMetadataDiscovery {
    private final EcosMetadataClient client;
    private final EcosMetadataParser parser;
    private final EcosPagePlanner planner = new EcosPagePlanner();

    public EcosMetadataDiscovery(EcosMetadataClient client, ObjectMapper objectMapper) {
        if (client == null) throw new IllegalArgumentException("client must not be null");
        this.client = client;
        this.parser = new EcosMetadataParser(objectMapper);
    }

    public EcosMetadataDiscoveryResult discover(
            String statCode,
            EcosDiscoveryBudget budget) {
        if (statCode == null || statCode.isBlank()) {
            throw new IllegalArgumentException("statCode must not be blank");
        }
        if (budget == null) throw new IllegalArgumentException("budget must not be null");

        EcosPageRange firstRange = planner.firstPage();
        EcosMetadataPage firstPage = fetchAndParse(statCode, firstRange);
        long totalCount = firstPage.totalCount();
        int expectedFirstRows = (int) Math.min(totalCount, 1000L);
        if (firstPage.items().size() != expectedFirstRows) {
            throw malformed("first metadata page row count does not match totalCount");
        }

        List<EcosPageRange> remaining = planner.remainingPages(totalCount);
        int requiredPageRequests = 1 + remaining.size();
        int worstCaseHttpAttempts = Math.multiplyExact(
                requiredPageRequests, EcosRequestExecutionGuard.MAX_ATTEMPTS);

        enforceBudget(budget, totalCount, requiredPageRequests, worstCaseHttpAttempts);

        List<EcosMetadataItem> items = new ArrayList<>();
        items.addAll(firstPage.items());
        for (EcosPageRange range : remaining) {
            EcosMetadataPage page = fetchAndParse(statCode, range);
            if (page.totalCount() != totalCount) {
                throw malformed("list_total_count changed between metadata pages");
            }
            long expectedRows = range.endRow() - range.startRow() + 1;
            if (page.items().size() != expectedRows) {
                throw malformed("metadata page row count does not match requested range");
            }
            items.addAll(page.items());
        }

        return new EcosMetadataDiscoveryResult(
                totalCount, requiredPageRequests, worstCaseHttpAttempts, items);
    }
    private EcosMetadataPage fetchAndParse(String statCode, EcosPageRange range) {
        EcosRequestDescriptor request = new EcosRequestDescriptor(
                range.startRow(), range.endRow(), statCode);
        EcosRawResponse raw = client.fetch(request);
        return parser.parse(raw, statCode);
    }

    private static void enforceBudget(
            EcosDiscoveryBudget budget,
            long totalCount,
            int requiredPageRequests,
            int worstCaseHttpAttempts) {
        if (totalCount > budget.maxRows()) {
            throw new EcosDiscoveryBudgetExceededException(
                    "ECOS metadata row budget exceeded before additional requests");
        }
        if (requiredPageRequests > budget.maxPageRequests()) {
            throw new EcosDiscoveryBudgetExceededException(
                    "ECOS metadata page-request budget exceeded before additional requests");
        }
        if (worstCaseHttpAttempts > budget.maxHttpAttempts()) {
            throw new EcosDiscoveryBudgetExceededException(
                    "ECOS metadata HTTP-attempt budget exceeded before additional requests");
        }
    }

    private static EcosProviderException malformed(String message) {
        return new EcosProviderException(
                EcosProviderException.Category.MALFORMED_RESPONSE, message);
    }
}
