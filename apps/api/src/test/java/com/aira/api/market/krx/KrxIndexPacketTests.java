package com.aira.api.market.krx;

import static org.junit.jupiter.api.Assertions.*;
import com.aira.api.market.domain.FactPredicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KrxIndexPacketTests {
    @Test void exactRepresentativeIndexParsesSignedCoreMetrics() {
        var snapshot = snapshot(KrxDataset.KOSPI_INDEX, "KOSPI", "2650.21", "-15.20", "-0.57");
        var packet = KrxIndexPacket.from(snapshot);
        assertEquals("KOSPI", packet.market());
        assertEquals(new BigDecimal("2650.21"), value(packet, FactPredicate.INDEX_CLOSE));
        assertEquals(new BigDecimal("-15.20"), value(packet, FactPredicate.INDEX_CHANGE));
        assertEquals(new BigDecimal("-0.57"), value(packet, FactPredicate.INDEX_CHANGE_RATE));
    }

    @Test void explicitKoreanRepresentativeNameIsAccepted() {
        var packet = KrxIndexPacket.from(snapshot(KrxDataset.KOSDAQ_INDEX, "코스닥", "850.10", "+3.20", "+0.38"));
        assertEquals("KOSDAQ", packet.market());
        assertEquals(new BigDecimal("0.38"), value(packet, FactPredicate.INDEX_CHANGE_RATE));
    }

    @Test void ambiguousOrIncompleteRepresentativeIndexIsRejected() {
        LocalDate date = LocalDate.of(2035, 1, 8);
        var ambiguous = KrxSnapshot.validated(KrxDataset.KOSPI_INDEX, date, List.of(
                row(date, "KOSPI", "2650.21", "1", "0.04"),
                row(date, "KOSPI", "2600.00", "2", "0.08")));
        assertThrows(IllegalArgumentException.class, () -> KrxIndexPacket.from(ambiguous));
        var incomplete = KrxSnapshot.validated(KrxDataset.KOSPI_INDEX, date,
                List.of(row(date, "KOSPI", "2650.21", "-", "-0.57")));
        assertThrows(IllegalArgumentException.class, () -> KrxIndexPacket.from(incomplete));
    }

    private static KrxSnapshot snapshot(KrxDataset dataset, String name, String close, String change, String rate) {
        LocalDate date = LocalDate.of(2035, 1, 8);
        return KrxSnapshot.validated(dataset, date, List.of(row(date, name, close, change, rate)));
    }

    private static Map<String, String> row(LocalDate date, String name, String close, String change, String rate) {
        return Map.of("BAS_DD", date.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE),
                "IDX_CLSS", name, "IDX_NM", name, "CLSPRC_IDX", close,
                "CMPPREVDD_IDX", change, "FLUC_RT", rate);
    }

    private static BigDecimal value(KrxIndexPacket packet, FactPredicate predicate) {
        return packet.values().stream().filter(item -> item.predicate() == predicate)
                .findFirst().orElseThrow().value();
    }
}
