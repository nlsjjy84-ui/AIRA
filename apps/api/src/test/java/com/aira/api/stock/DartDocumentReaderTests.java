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

    static String report(String title, String tables) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?><DOCUMENT><BODY><SECTION-2><TITLE ATOC=\"Y\">1. 사업의 개요</TITLE><TABLE><TR><TD>매출</TD><TD>1</TD></TR></TABLE></SECTION-2>"
                + "<SECTION-2><TITLE ATOC=\"Y\">" + title + "</TITLE>" + tables + "</SECTION-2><SECTION-2><TITLE ATOC=\"Y\">3. 원재료</TITLE><TABLE><TR><TD>매출</TD><TD>99</TD></TR></TABLE></SECTION-2></BODY></DOCUMENT>";
    }

    @Test
    void readsSegmentShares() throws Exception {
        // 삼성전자 2025 사업보고서 양식
        String xml = report("2. 주요 제품 및 서비스", "<TABLE><TR><TD>(단위 : 억원, %)</TD></TR></TABLE><TABLE>"
                + "<TR><TH>부 문</TH><TH>주요 제품</TH><TH>매출액</TH><TH>비중</TH></TR>"
                + "<TR><TD>DX 부문</TD><TD>TV, 모니터, 냉장고, 세탁기, 에어컨, 스마트폰, 네트워크시스템, PC 등</TD><TE>1,879,673</TE><TE>56.3%</TE></TR>"
                + "<TR><TD>DS 부문</TD><TD>DRAM, NAND Flash, 모바일AP 등</TD><TE>1,301,282</TE><TE>39.0%</TE></TR>"
                + "<TR><TD>기타</TD><TD>부문간 내부거래 제거 등</TD><TE>△301,146</TE><TE>△8.9%</TE></TR>"
                + "<TR><TD>총 계</TD><TE>3,336,059</TE><TE>100.00%</TE></TR></TABLE>");
        var mix = DartDocumentReader.salesMix(zip("a.xml", xml.getBytes("UTF-8")));
        assertEquals(java.util.List.of("DX 부문", "DS 부문", "기타"), mix.items().stream().map(DartDocumentReader.MixItem::name).toList());
        assertEquals(0, new BigDecimal("56.3").compareTo(mix.items().get(0).share()));
        assertEquals(0, new BigDecimal("187967300000000").compareTo(mix.items().get(0).amount()));
        assertEquals(0, new BigDecimal("-8.9").compareTo(mix.items().get(2).share()));
    }

    @Test
    void followsMergedRowsAndCombinedAmountCells() throws Exception {
        // 품목 칸이 있고, 위 칸과 합쳐진 줄은 왼쪽 칸이 빠진다(듀켐바이오 양식). 매출액(비율)이 한 칸인 양식(SK하이닉스)도 읽는다.
        String xml = report("2. 주요 제품 및 서비스", "<TABLE><TR><TD>(단위 : 천원,%)</TD></TR></TABLE><TABLE>"
                + "<TR><TD>사업부문</TD><TD>매출유형</TD><TD>품 목</TD><TD>용 도</TD><TD>제23기</TD></TR><TR><TD>매출액</TD><TD>비율</TD></TR>"
                + "<TR><TD>방사성 의약품</TD><TD>제품매출</TD><TD>FDG</TD><TD>악성종양 진단용</TD><TD>22,017,140</TD><TD>57.1%</TD></TR>"
                + "<TR><TD>Neuraceq,Vizamyl</TD><TD>알츠하이머 치매진단용</TD><TD>7,501,071</TD><TD>19.4%</TD></TR>"
                + "<TR><TD>소 계</TD><TD>33,813,621</TD><TD>87.7%</TD></TR>"
                + "<TR><TD>상품매출</TD><TD>조영제</TD><TD>조영제</TD><TD>1,478,779</TD><TD>3.8%</TD></TR>"
                + "<TR><TD>매출 총 합 계</TD><TD>38,572,364</TD><TD>100.0%</TD></TR><TR><TD>(내수매출)</TD><TD>38,459,836</TD><TD>99.7%</TD></TR></TABLE>");
        var mix = DartDocumentReader.salesMix(zip("b.xml", xml.getBytes("UTF-8")));
        assertEquals(java.util.List.of("FDG", "Neuraceq,Vizamyl", "조영제"), mix.items().stream().map(DartDocumentReader.MixItem::name).toList());
        String sk = report("2. 주요 제품 및 서비스", "<TABLE><TR><TD>[제78기 누계]</TD><TD>(단위: 백만원)</TD></TR></TABLE><TABLE>"
                + "<TR><TD>사업부문</TD><TD>매출유형</TD><TD>품목</TD><TD>구체적용도</TD><TD>주요상표등</TD><TD>매출액(비율)</TD></TR>"
                + "<TR><TD>반도체 부문</TD><TD>제품 외</TD><TD>DRAM, NAND Flash 등</TD><TD>산업용 전자기기</TD><TD>SK하이닉스</TD><TD>97,146,675(100%)</TD></TR>"
                + "<TR><TD>합계</TD><TD>97,146,675(100%)</TD></TR></TABLE>");
        var one = DartDocumentReader.salesMix(zip("c.xml", sk.getBytes("UTF-8")));
        assertEquals(1, one.items().size());
        assertEquals("DRAM, NAND Flash 등", one.items().get(0).name());
        assertEquals(0, new BigDecimal("100").compareTo(one.items().get(0).share()));
    }
}
