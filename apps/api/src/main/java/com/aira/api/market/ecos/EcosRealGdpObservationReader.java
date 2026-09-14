package com.aira.api.market.ecos;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public final class EcosRealGdpObservationReader {
    private static final long PAGE_SIZE = 1000L;
    private final EcosRealGdpStatisticSearchClient client;
    private final EcosPagePlanner planner = new EcosPagePlanner();

    EcosRealGdpObservationReader(EcosRealGdpStatisticSearchClient client) {
        if (client == null) throw new IllegalArgumentException("client must not be null");
        this.client = client;
    }

    public EcosRealGdpObservationResult read(EcosRealGdpObservationScope scope) {
        if (scope == null) throw new IllegalArgumentException("scope must not be null");
        long requestedQuarters = scope.quarterCount();
        int plannedPages = pageCount(requestedQuarters);
        int plannedWorstCaseAttempts = Math.multiplyExact(
                plannedPages, EcosRequestExecutionGuard.MAX_ATTEMPTS);
        enforceBudget(scope.budget(), requestedQuarters, plannedPages, plannedWorstCaseAttempts);
        long firstEnd = Math.min(PAGE_SIZE, requestedQuarters);
        EcosStatisticSearchPage first = fetch(scope, new EcosPageRange(1, firstEnd));
        long totalCount = first.totalCount();
        if (totalCount > requestedQuarters) {
            throw malformed("StatisticSearch returned more rows than requested quarters");
        }
        int expectedFirstRows = Math.toIntExact(Math.min(totalCount, firstEnd));
        if (first.observations().size() != expectedFirstRows) {
            throw malformed("first observation page row count does not match totalCount");
        }

        List<EcosPageRange> remaining = planner.remainingPages(totalCount);
        int actualPageRequests = 1 + remaining.size();
        int actualWorstCaseAttempts = Math.multiplyExact(
                actualPageRequests, EcosRequestExecutionGuard.MAX_ATTEMPTS);

        List<EcosStatisticSearchObservation> observations = new ArrayList<>();
        Set<String> times = new HashSet<>();
        addPage(scope, first, observations, times);
        for (EcosPageRange range : remaining) {
            EcosStatisticSearchPage page = fetch(scope, range);
            if (page.totalCount() != totalCount) {
                throw malformed("list_total_count changed between observation pages");
            }
            long expectedRows = range.endRow() - range.startRow() + 1L;
            if (page.observations().size() != expectedRows) {
                throw malformed("observation page row count does not match requested range");
            }
            addPage(scope, page, observations, times);
        }
        if (observations.size() != totalCount) {
            throw malformed("assembled observation count does not match list_total_count");
        }

        return new EcosRealGdpObservationResult(
                scope.startTime(), scope.endTime(), requestedQuarters,
                totalCount, actualPageRequests, actualWorstCaseAttempts, observations);
    }

    private EcosStatisticSearchPage fetch(
            EcosRealGdpObservationScope scope, EcosPageRange range) {
        return client.fetch(new EcosRealGdpSearchRequest(
                range.startRow(), range.endRow(), scope.startTime(), scope.endTime()));
    }
    private static void addPage(
            EcosRealGdpObservationScope scope,
            EcosStatisticSearchPage page,
            List<EcosStatisticSearchObservation> target,
            Set<String> times) {
        for (EcosStatisticSearchObservation observation : page.observations()) {
            if (!scope.contains(observation.time())) {
                throw malformed("StatisticSearch TIME is outside requested quarter scope");
            }
            if (!times.add(observation.time())) {
                throw malformed("duplicate StatisticSearch TIME across pages");
            }
            target.add(observation);
        }
    }

    private static int pageCount(long rowCount) {
        return Math.toIntExact((rowCount + PAGE_SIZE - 1L) / PAGE_SIZE);
    }
    private static void enforceBudget(
            EcosObservationBudget budget,
            long quarters,
            int pageRequests,
            int worstCaseHttpAttempts) {
        if (quarters > budget.maxQuarters()) {
            throw new EcosObservationBudgetExceededException(
                    "ECOS observation quarter budget exceeded before request");
        }
        if (pageRequests > budget.maxPageRequests()) {
            throw new EcosObservationBudgetExceededException(
                    "ECOS observation page-request budget exceeded before request");
        }
        if (worstCaseHttpAttempts > budget.maxHttpAttempts()) {
            throw new EcosObservationBudgetExceededException(
                    "ECOS observation HTTP-attempt budget exceeded before request");
        }
    }

    private static EcosProviderException malformed(String message) {
        return new EcosProviderException(
                EcosProviderException.Category.MALFORMED_RESPONSE, message);
    }
}
