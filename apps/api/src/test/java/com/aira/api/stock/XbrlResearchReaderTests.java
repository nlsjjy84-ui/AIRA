package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** XBRL 원본 파일에서 연구개발비(비용)를 1년·연결 기준으로 고른다. 개발비 자산·사업부문 값은 쓰지 않는다. */
class XbrlResearchReaderTests {
    private static byte[] zip(String instance) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry("entity00126380_2025-12-31_lab-ko.xml"));
            z.write("<labels/>".getBytes(StandardCharsets.UTF_8));
            z.putNextEntry(new ZipEntry("entity00126380_2025-12-31.xbrl"));
            z.write(instance.getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }

    private static String context(String id, String start, String end, String member) {
        return "<xbrli:context id=\"" + id + "\"><xbrli:entity><xbrli:identifier scheme=\"x\">1</xbrli:identifier>"
                + (member == null ? "" : "<xbrli:segment><xbrldi:explicitMember dimension=\"ifrs-full:ConsolidatedAndSeparateFinancialStatementsAxis\">"
                + member + "</xbrldi:explicitMember></xbrli:segment>")
                + "</xbrli:entity><xbrli:period><xbrli:startDate>" + start + "</xbrli:startDate><xbrli:endDate>" + end
                + "</xbrli:endDate></xbrli:period></xbrli:context>";
    }

    @Test
    void picksTheConsolidatedAnnualResearchExpense() throws Exception {
        String instance = "<xbrli:xbrl xmlns:xbrli=\"http://www.xbrl.org/2003/instance\" xmlns:xbrldi=\"http://xbrl.org/2006/xbrldi\""
                + " xmlns:ifrs-full=\"http://xbrl.ifrs.org/taxonomy/2023-03-23/ifrs-full\" xmlns:dart=\"http://dart.fss.or.kr/te/2023\">"
                + context("CFY", "2025-01-01", "2025-12-31", "ifrs-full:ConsolidatedMember")
                + context("SFY", "2025-01-01", "2025-12-31", "ifrs-full:SeparateMember")
                + context("PFY", "2024-01-01", "2024-12-31", "ifrs-full:ConsolidatedMember")
                + "<ifrs-full:ResearchAndDevelopmentExpense contextRef=\"SFY\" unitRef=\"KRW\" decimals=\"-6\">30000000000000</ifrs-full:ResearchAndDevelopmentExpense>"
                + "<ifrs-full:ResearchAndDevelopmentExpense contextRef=\"PFY\" unitRef=\"KRW\" decimals=\"-6\">35000000000000</ifrs-full:ResearchAndDevelopmentExpense>"
                + "<ifrs-full:ResearchAndDevelopmentExpense contextRef=\"CFY\" unitRef=\"KRW\" decimals=\"-6\">37000000000000</ifrs-full:ResearchAndDevelopmentExpense>"
                + "<dart:CapitalisedDevelopmentExpenditureIntangible contextRef=\"CFY\" unitRef=\"KRW\">1</dart:CapitalisedDevelopmentExpenditureIntangible>"
                + "</xbrli:xbrl>";
        var found = XbrlResearchReader.read(zip(instance), 2025);
        assertEquals(0, new BigDecimal("37000000000000").compareTo(found.amount()));
        assertEquals("CFS", found.basis());
        assertTrue(found.element().endsWith("ResearchAndDevelopmentExpense"));
    }

    @Test
    void noValueWhenTheNotesAreNotTaggedOrTheFileIsNotAZip() throws Exception {
        String instance = "<xbrli:xbrl xmlns:xbrli=\"http://www.xbrl.org/2003/instance\">"
                + context("CFY", "2025-01-01", "2025-12-31", null) + "</xbrli:xbrl>";
        assertNull(XbrlResearchReader.read(zip(instance), 2025));
        assertNull(XbrlResearchReader.read("<result><status>013</status></result>".getBytes(StandardCharsets.UTF_8), 2025));
        assertFalse(XbrlResearchReader.isResearchExpense("ResearchAndDevelopmentExpenseRatio"));
        assertTrue(XbrlResearchReader.isResearchExpense("ResearchAndDevelopmentCosts"));
    }
}
