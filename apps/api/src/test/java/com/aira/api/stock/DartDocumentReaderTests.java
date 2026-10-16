package com.aira.api.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;

/** 공시서류 원본에서 연구개발비용 표와 잠정실적 표를 읽는다. 단위는 표 앞 「단위」 문구를 따른다. */
class DartDocumentReaderTests {
    private static byte[] zip(String name, byte[] body) throws Exception {
        var out = new ByteArrayOutputStream();
        try (var z = new ZipOutputStream(out)) {
            z.putNextEntry(new ZipEntry(name));
            z.write(body);
        }
        return out.toByteArray();
    }

    // 삼성전자 2025 사업보고서 「6. 주요계약 및 연구개발활동」 표 양식(dart4 XML)
    static final String REPORT = "<?xml version=\"1.0\" encoding=\"utf-8\"?><DOCUMENT><BODY>"
            + "<P>나. 연구개발활동의 개요 및 연구개발비용</P>"
            + "<TABLE><TBODY><TR><TD>[연구개발비용]</TD><TD>(단위 : 백만원, %)</TD></TR></TBODY></TABLE>"
            + "<TABLE><THEAD><TR><TH>과     목</TH><TH>제57기</TH><TH>제56기</TH><TH>제55기</TH></TR></THEAD><TBODY>"
            + "<TR><TD>연구개발비용 총계</TD><TE>37,754,815</TE><TE>35,021,531</TE><TE>28,352,769</TE></TR>"
            + "<TR><TD>(정부보조금)</TD><TE>△14,423</TE><TE>△23,389</TE><TE>△13,045</TE></TR>"
            + "<TR><TD>연구개발비용 계</TD><TE>37,740,392</TE><TE>34,998,142</TE><TE>28,339,724</TE></TR>"
            + "</TBODY></TABLE></BODY></DOCUMENT>";

    @Test
    void readsTheResearchTotalInWon() throws Exception {
        var found = DartDocumentReader.research(DartDocumentReader.tables(zip("20260310002820.xml", REPORT.getBytes("UTF-8"))));
        assertEquals(new BigDecimal("37754815000000"), found.amount());
        assertEquals("연구개발비용 총계", found.label());
    }

    @Test
    void noUnitMeansNoValue() throws Exception {
        String html = "<table><tr><td>연구개발비용 총계</td><td>1,000</td></tr></table>";
        assertNull(DartDocumentReader.research(DartDocumentReader.tables(zip("a.xml", html.getBytes("UTF-8")))));
    }

    // 삼성전자 2026.10.08 연결재무제표기준영업(잠정)실적(공정공시) 표 양식(EUC-KR HTML)
    static final String PRELIM = "<html><head><meta content=\"text/html; charset=euc-kr\" http-equiv=\"Content-Type\"></head><body>"
            + "<table><tr><td>당기실적</td><td>2026-07-01</td><td>~</td><td>2026-09-30</td></tr></table>"
            + "<table><tr><td>※ 동 정보는 잠정치로서 향후 확정치와는 다를 수 있음.</td></tr>"
            + "<tr><td>1. 연결실적내용</td><td><span>단위 : 조원, %</span></td></tr>"
            + "<tr><td>구분</td><td>당기실적</td><td>전기실적</td><td>전기대비</td><td>전년동기실적</td><td>전년동기대비</td></tr>"
            + "<tr><td>(26.3Q)</td><td>(26.2Q)</td><td>증감율(%)</td><td>흑자적자전환여부</td><td>(25.3Q)</td><td>증감율(%)</td><td>흑자적자전환여부</td></tr>"
            + "<tr><td>매출액</td><td>당해실적</td><td>195.00</td><td>171.50</td><td>13.70</td><td>-</td><td>86.06</td><td>126.59</td><td>-</td></tr>"
            + "<tr><td>누계실적</td><td>500.37</td><td>-</td><td>-</td><td>-</td><td>239.77</td><td>108.69</td><td>-</td></tr>"
            + "<tr><td>영업이익</td><td>당해실적</td><td>107.40</td><td>89.49</td><td>20.01</td><td>-</td><td>12.17</td><td>782.50</td><td>-</td></tr>"
            + "<tr><td>누계실적</td><td>254.13</td><td>-</td><td>-</td><td>-</td><td>23.53</td><td>980.03</td><td>-</td></tr>"
            + "<tr><td>당기순이익</td><td>당해실적</td><td>-</td><td>-</td><td>-</td><td>-</td><td>-</td><td>-</td><td>-</td></tr>"
            + "</table></body></html>";

    @Test
    void readsPreliminaryQuarterInWon() throws Exception {
        var found = DartDocumentReader.preliminary(DartDocumentReader.tables(zip("20261008800004.htm", PRELIM.getBytes(Charset.forName("MS949")))));
        assertEquals("2026-Q3", found.period());
        assertEquals(0, new BigDecimal("195000000000000").compareTo(found.values().get("REVENUE")));
        assertEquals(0, new BigDecimal("107400000000000").compareTo(found.values().get("OPERATING_INCOME")));
        assertEquals(0, new BigDecimal("254130000000000").compareTo(found.cumulative().get("OPERATING_INCOME")));
        assertNull(found.values().get("NET_INCOME"));
    }
}
