package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class EcosPagePlannerTests {
    private final EcosPagePlanner planner = new EcosPagePlanner();

    @Test void firstPageIsExactlyOneThroughOneThousand() {
        assertEquals(new EcosPageRange(1, 1000), planner.firstPage());
    }

    @Test void noRemainingPagesThroughOneThousand() {
        for (long total : List.of(0L, 1L, 999L, 1000L)) {
            assertEquals(List.of(), planner.remainingPages(total));
        }
    }

    @Test void plansExactRemainingRanges() {
        assertEquals(List.of(new EcosPageRange(1001, 1001)), planner.remainingPages(1001));
        assertEquals(List.of(new EcosPageRange(1001, 2000)), planner.remainingPages(2000));
        assertEquals(List.of(new EcosPageRange(1001, 2000), new EcosPageRange(2001, 2001)),
                planner.remainingPages(2001));
    }

    @Test void rejectsNegativeTotalCount() {
        assertThrows(IllegalArgumentException.class, () -> planner.remainingPages(-1));
    }
}
