package com.aira.api.market.opendart;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class OpenDartCompanyDirectoryParserTests {
    private final OpenDartCompanyDirectoryParser parser = new OpenDartCompanyDirectoryParser();

    @Test
    void parsesOfficialZipXmlShapeAndPreservesLeadingZero() throws Exception {
        var directory = parser.parse(zip("CORPCODE.xml", fixture()));

        var samsung = directory.findByCorpCode("00126380").orElseThrow();
        assertEquals("00126380", samsung.corpCode());
        assertEquals("삼성전자(주)", samsung.corpName());
        assertEquals("SAMSUNG ELECTRONICS CO.,LTD.", samsung.corpEnglishName());
        assertEquals("005930", samsung.stockCode());
        assertEquals(LocalDate.of(2025, 8, 29), samsung.modifiedDate());
    }

    @Test
    void supportsExactStockCodeLookupAndOptionalBlankStockCode() throws Exception {
        var directory = parser.parse(zip("CORPCODE.xml", fixture()));

        assertEquals("00126380", directory.findByStockCode("005930").orElseThrow().corpCode());
        var unlisted = directory.findByCorpCode("00401731").orElseThrow();
        assertNull(unlisted.stockCode());
        assertNull(unlisted.modifiedDate());
        assertThrows(IllegalArgumentException.class, () -> directory.findByStockCode("5930"));
    }

    @Test
    void unknownCorpCodeReturnsEmptyWithoutNameFallback() throws Exception {
        var directory = parser.parse(zip("CORPCODE.xml", fixture()));

        assertEquals(java.util.Optional.empty(), directory.findByCorpCode("99999999"));
    }

    @Test
    void rejectsMalformedXml() throws Exception {
        var failure = assertThrows(OpenDartProviderException.class,
                () -> parser.parse(zip("CORPCODE.xml", "<result><list>")));
        assertEquals(OpenDartProviderException.Category.MALFORMED_RESPONSE, failure.category());
        assertTrue(failure.getMessage().contains("XML structure is malformed"));
        assertTrue(failure.getMessage().contains("line="));
    }

    @Test
    void rejectsZipTraversalAndMultipleUnexpectedEntries() throws Exception {
        assertThrows(OpenDartProviderException.class,
                () -> parser.parse(zip("../CORPCODE.xml", fixture())));
        assertThrows(OpenDartProviderException.class,
                () -> parser.parse(zipTwo("CORPCODE.xml", fixture(), "extra.xml", fixture())));
    }

    @Test
    void rejectsInvalidOfficialIdentifiersAndDates() throws Exception {
        String invalidCorpCode = fixture().replace("00126380", "126380");
        var corpFailure = assertThrows(OpenDartProviderException.class,
                () -> parser.parse(zip("CORPCODE.xml", invalidCorpCode)));
        assertDiagnostic(corpFailure, "record=1", "field=corp_code", "presence=present",
                "length=6", "expected=8 digits");

        String invalidDate = fixture().replace("20250829", "2025-08-29");
        var dateFailure = assertThrows(OpenDartProviderException.class,
                () -> parser.parse(zip("CORPCODE.xml", invalidDate)));
        assertDiagnostic(dateFailure, "record=1", "field=modify_date", "presence=present",
                "length=10", "expected=blank or YYYYMMDD");
    }

    @Test
    void diagnosticsIdentifyLaterRecordAndMissingOrInvalidFieldsWithoutValues() throws Exception {
        String missingName = fixture().replace("<corp_name>비상장회사</corp_name>", "");
        var nameFailure = assertThrows(OpenDartProviderException.class,
                () -> parser.parse(zip("CORPCODE.xml", missingName)));
        assertDiagnostic(nameFailure, "record=2", "field=corp_name", "presence=absent",
                "length=0", "expected=non-blank text");

        String invalidStock = fixture().replace("<stock_code>005930</stock_code>",
                "<stock_code>SECRET-STOCK-VALUE</stock_code>");
        var stockFailure = assertThrows(OpenDartProviderException.class,
                () -> parser.parse(zip("CORPCODE.xml", invalidStock)));
        assertDiagnostic(stockFailure, "record=1", "field=stock_code", "presence=present",
                "length=18", "expected=blank or 6 non-whitespace characters");
        assertTrue(!stockFailure.getMessage().contains("SECRET-STOCK-VALUE"));
    }

    @Test
    void acceptsOfficialSixCharacterAlphanumericStockCodes() throws Exception {
        String alphanumeric = fixture().replace("005930", "A12345");
        var directory = parser.parse(zip("CORPCODE.xml", alphanumeric));

        assertEquals("00126380",
                directory.findByStockCode("A12345").orElseThrow().corpCode());
    }

    private static String fixture() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <result>
                  <list><corp_code>00126380</corp_code><corp_name>삼성전자(주)</corp_name>
                    <corp_eng_name>SAMSUNG ELECTRONICS CO.,LTD.</corp_eng_name>
                    <stock_code>005930</stock_code><modify_date>20250829</modify_date></list>
                  <list><corp_code>00401731</corp_code><corp_name>비상장회사</corp_name>
                    <corp_eng_name></corp_eng_name><stock_code> </stock_code>
                    <modify_date> </modify_date></list>
                </result>
                """;
    }

    private static byte[] zip(String name, String xml) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static byte[] zipTwo(String firstName, String first, String secondName, String second)
            throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(firstName));
            zip.write(first.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(secondName));
            zip.write(second.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private static void assertDiagnostic(Throwable failure, String... fragments) {
        for (String fragment : fragments) {
            assertTrue(failure.getMessage().contains(fragment), failure.getMessage());
        }
    }
}
