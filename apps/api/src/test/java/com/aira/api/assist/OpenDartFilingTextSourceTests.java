package com.aira.api.assist;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

class OpenDartFilingTextSourceTests {
    private static byte[] zip(String name, byte[] body) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(body);
            zip.closeEntry();
        }
        return out.toByteArray();
    }

    @Test
    void readsTextFromAZippedDocument() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><DOCUMENT><P>계약금액은 100억원입니다.</P></DOCUMENT>";
        String text = OpenDartFilingTextSource.parse(zip("a.xml", xml.getBytes(StandardCharsets.UTF_8)));
        assertTrue(text.contains("계약금액은 100억원입니다."));
    }

    @Test
    void honoursADeclaredLegacyEncoding() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"euc-kr\"?><DOCUMENT><P>계약금액은 100억원입니다.</P></DOCUMENT>";
        String text = OpenDartFilingTextSource.parse(zip("a.xml", xml.getBytes("EUC-KR")));
        assertTrue(text.contains("계약금액은 100억원입니다."));
    }

    @Test
    void mapsProviderStatusCodesToShortCodes() {
        assertEquals("NOT_FOUND", code("<result><status>013</status><message>x</message></result>"));
        assertEquals("RATE", code("<result><status>020</status><message>x</message></result>"));
        assertEquals("NO_KEY", code("<result><status>010</status><message>x</message></result>"));
        assertEquals("FAILED:STATUS_800", code("<result><status>800</status><message>x</message></result>"));
        assertTrue(code("<html>oops</html>").startsWith("FAILED:NOT_ZIP_"));
    }

    private static String code(String body) {
        try {
            OpenDartFilingTextSource.parse(body.getBytes(StandardCharsets.UTF_8));
            return "OK";
        } catch (IllegalStateException failure) {
            return failure.getMessage();
        }
    }

    @Test
    void refusesToFetchWithoutAKeyOrAValidReceipt() {
        var source = new OpenDartFilingTextSource(null, "", "http://localhost/none");
        assertEquals("NO_KEY", assertThrows(IllegalStateException.class, () -> source.fetch("20260101000001")).getMessage());
        assertEquals("FAILED", assertThrows(IllegalStateException.class, () -> source.fetch("12")).getMessage());
    }
}
