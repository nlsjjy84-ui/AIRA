package com.aira.api.market.krx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class KrxPreparedPacketSelectionTests {
    @Test
    void retainsFullOfficialSnapshotsButFiltersPromotedSecuritiesAndValues() {
        LocalDate date = LocalDate.of(2035, 1, 12);
        var base = KrxSnapshot.validated(KrxDataset.STK_BASE, date, List.of(
                Map.of("ISU_CD", "KR7000660001", "ISU_SRT_CD", "000660",
                        "ISU_NM", "SK hynix common", "ISU_ABBRV", "SK hynix"),
                Map.of("ISU_CD", "KR7035420009", "ISU_SRT_CD", "035420",
                        "ISU_NM", "NAVER common", "ISU_ABBRV", "NAVER"),
                Map.of("ISU_CD", "KR7005930003", "ISU_SRT_CD", "005930",
                        "ISU_NM", "Samsung common", "ISU_ABBRV", "Samsung")));
        var daily = KrxSnapshot.validated(KrxDataset.STK_DAILY, date, List.of(
                Map.of("BAS_DD", "20350112", "ISU_CD", "000660", "TDD_CLSPRC", "100", "ACC_TRDVOL", "10"),
                Map.of("BAS_DD", "20350112", "ISU_CD", "035420", "TDD_CLSPRC", "200", "ACC_TRDVOL", "20"),
                Map.of("BAS_DD", "20350112", "ISU_CD", "005930", "TDD_CLSPRC", "300", "ACC_TRDVOL", "30")));

        var selected = KrxPreparedPacket.stock(base, daily)
                .selectShortCodes(Set.of("000660", "035420"));

        assertEquals(base, selected.base());
        assertEquals(daily, selected.daily());
        assertEquals(Set.of("000660", "035420"), selected.securities().stream()
                .map(KrxPreparedPacket.SecurityRow::shortCode)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(Set.of("SK hynix", "NAVER"), selected.securities().stream()
                .map(KrxPreparedPacket.SecurityRow::name)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(4, selected.values().size());
        assertThrows(IllegalArgumentException.class,
                () -> KrxPreparedPacket.stock(base, daily).selectShortCodes(Set.of("123456")));
    }
}
