package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

class OpenDartCompanyDirectorySnapshotTests {
    @Test
    @EnabledIfEnvironmentVariable(named = "OPENDART_DIRECTORY_SNAPSHOT", matches = ".+")
    void parsesSingleFetchedOfficialSnapshot() throws Exception {
        byte[] snapshot = Files.readAllBytes(Path.of(System.getenv("OPENDART_DIRECTORY_SNAPSHOT")));

        var directory = new OpenDartCompanyDirectoryParser().parse(snapshot);

        assertFalse(directory.records().isEmpty());
        assertTrue(directory.records().stream()
                .allMatch(record -> record.corpCode().matches("[0-9]{8}")));
        assertTrue(directory.records().stream()
                .filter(record -> record.stockCode() != null)
                .allMatch(record -> record.stockCode().matches("\\S{6}")));
        assertTrue(directory.findByCorpCode("00126380").isPresent());
        assertTrue(directory.findByStockCode("005930").isPresent());
    }
}
