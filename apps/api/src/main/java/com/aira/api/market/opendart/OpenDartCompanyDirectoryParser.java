package com.aira.api.market.opendart;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;

public final class OpenDartCompanyDirectoryParser {
    private static final int MAX_ENTRIES = 2;
    private static final int MAX_XML_BYTES = 100 * 1024 * 1024;
    private static final DateTimeFormatter MODIFIED_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    public OpenDartCompanyDirectory parse(byte[] zipBytes) {
        if (zipBytes == null || zipBytes.length == 0) {
            throw malformed("company directory ZIP is empty");
        }
        return parseXml(readSingleSafeXml(zipBytes));
    }

    private static byte[] readSingleSafeXml(byte[] zipBytes) {
        byte[] xml = null;
        int entries = 0;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ENTRIES) {
                    throw malformed("company directory ZIP contains too many entries");
                }
                String name = entry.getName();
                if (entry.isDirectory() || name.contains("/") || name.contains("\\")
                        || name.contains("..") || !name.toLowerCase(java.util.Locale.ROOT).endsWith(".xml")) {
                    throw malformed("company directory ZIP entry is unsafe");
                }
                if (xml != null) {
                    throw malformed("company directory ZIP contains multiple XML entries");
                }
                var output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int total = 0;
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    total += read;
                    if (total > MAX_XML_BYTES) {
                        throw malformed("company directory XML is too large");
                    }
                    output.write(buffer, 0, read);
                }
                xml = output.toByteArray();
            }
        } catch (IOException exception) {
            throw malformed("company directory ZIP could not be read");
        }
        if (xml == null || xml.length == 0) {
            throw malformed("company directory ZIP has no XML entry");
        }
        return xml;
    }

    private static OpenDartCompanyDirectory parseXml(byte[] xml) {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
        List<OpenDartCompanyDirectoryRecord> records = new ArrayList<>();
        Map<String, String> values = null;
        String field = null;
        int recordIndex = 0;
        try {
            var reader = factory.createXMLStreamReader(new ByteArrayInputStream(xml));
            while (reader.hasNext()) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("list".equals(name)) {
                        values = new HashMap<>();
                    } else if (values != null) {
                        field = name;
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS
                        || event == XMLStreamConstants.CDATA) && values != null && field != null) {
                    values.merge(field, reader.getText(), String::concat);
                } else if (event == XMLStreamConstants.END_ELEMENT) {
                    String name = reader.getLocalName();
                    if ("list".equals(name) && values != null) {
                        recordIndex++;
                        records.add(toRecord(values, recordIndex));
                        values = null;
                    }
                    field = null;
                }
            }
            reader.close();
        } catch (XMLStreamException exception) {
            var location = exception.getLocation();
            String position = location == null ? "unknown"
                    : "line=" + location.getLineNumber() + " column=" + location.getColumnNumber();
            throw malformed("company directory XML structure is malformed at " + position);
        }
        try {
            return new OpenDartCompanyDirectory(records);
        } catch (IllegalArgumentException exception) {
            throw malformed("company directory XML has no valid records");
        }
    }

    private static OpenDartCompanyDirectoryRecord toRecord(
            Map<String, String> values, int recordIndex) {
        String corpCode = requiredFormat(values.get("corp_code"), recordIndex,
                "corp_code", "[0-9]{8}", "8 digits");
        String corpName = requiredText(values.get("corp_name"), recordIndex, "corp_name");
        String corpEnglishName = trimmed(values.get("corp_eng_name"));
        String stockCode = optionalFormat(values.get("stock_code"), recordIndex,
                "stock_code", "\\S{6}", "blank or 6 non-whitespace characters");
        LocalDate modifiedDate = optionalDate(values.get("modify_date"), recordIndex);
        return new OpenDartCompanyDirectoryRecord(corpCode, corpName, corpEnglishName,
                stockCode, modifiedDate);
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }

    private static LocalDate optionalDate(String value, int recordIndex) {
        String normalized = trimmed(value);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(normalized, MODIFIED_DATE);
        } catch (DateTimeParseException exception) {
            throw fieldFailure(recordIndex, "modify_date", value, "blank or YYYYMMDD");
        }
    }

    private static String requiredText(String value, int recordIndex, String fieldName) {
        String normalized = trimmed(value);
        if (normalized == null || normalized.isEmpty()) {
            throw fieldFailure(recordIndex, fieldName, value, "non-blank text");
        }
        return normalized;
    }

    private static String requiredFormat(String value, int recordIndex, String fieldName,
            String pattern, String expected) {
        String normalized = requiredText(value, recordIndex, fieldName);
        if (!normalized.matches(pattern)) {
            throw fieldFailure(recordIndex, fieldName, value, expected);
        }
        return normalized;
    }

    private static String optionalFormat(String value, int recordIndex, String fieldName,
            String pattern, String expected) {
        String normalized = trimmed(value);
        if (normalized == null || normalized.isEmpty()) {
            return null;
        }
        if (!normalized.matches(pattern)) {
            throw fieldFailure(recordIndex, fieldName, value, expected);
        }
        return normalized;
    }

    private static OpenDartProviderException fieldFailure(
            int recordIndex, String fieldName, String value, String expected) {
        String presence = value == null ? "absent" : "present";
        int length = value == null ? 0 : value.trim().length();
        return malformed("company directory field validation failed: record=" + recordIndex
                + " field=" + fieldName + " presence=" + presence + " length=" + length
                + " expected=" + expected);
    }

    private static OpenDartProviderException malformed(String message) {
        return new OpenDartProviderException(
                OpenDartProviderException.Category.MALFORMED_RESPONSE,
                "OpenDART " + message);
    }
}
