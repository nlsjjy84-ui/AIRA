package com.aira.api.stock;

/** 공시 제목으로 화면 분류를 고른다. 분류는 보기 편하게 묶는 것일 뿐 공시의 법적 종류를 판정하지 않는다. */
final class FilingCategories {
    private FilingCategories() {}

    static String of(String title) {
        String t = title == null ? "" : title.replaceAll("\\s+", "");
        if (t.matches(".*(사업보고서|반기보고서|분기보고서).*") && !t.contains("주요사항")) return "PERIODIC";
        if (t.matches(".*(영업\\(잠정\\)실적|잠정실적|매출액또는손익구조|손익구조|실적).*")) return "EARNINGS";
        if (t.matches(".*(대량보유|임원ㆍ주요주주|임원·주요주주|최대주주|주식등의|소유상황|특정증권).*")) return "OWNERSHIP";
        if (t.matches(".*(주요사항보고서|자기주식|유상증자|무상증자|합병|분할|단일판매|공급계약|타법인주식|전환사채|신주인수권|배당|투자판단).*"))
            return "MATERIAL";
        return "OTHER";
    }
}
