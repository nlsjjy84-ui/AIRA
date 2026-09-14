package com.aira.api.market.opendart;

import com.aira.api.market.domain.EventType;
import java.util.List;

/** The approved DS005 scope is explicit so discovery cannot silently promote the HOLD endpoint. */
public final class OpenDartDs005Catalog {
    public record Endpoint(String key, EventType type, String title, int wave) {}
    private static final List<Endpoint> AUTOMATIC = List.of(
        e("piicDecsn", EventType.DISCLOSURE, "유상증자 결정", 1),
        e("fricDecsn", EventType.DISCLOSURE, "무상증자 결정", 1),
        e("pifricDecsn", EventType.DISCLOSURE, "유무상증자 결정", 1),
        e("crDecsn", EventType.DISCLOSURE, "감자 결정", 1),
        e("cvbdIsDecsn", EventType.DISCLOSURE, "전환사채권 발행결정", 2),
        e("bdwtIsDecsn", EventType.DISCLOSURE, "신주인수권부사채권 발행결정", 2),
        e("exbdIsDecsn", EventType.DISCLOSURE, "교환사채권 발행결정", 2),
        e("wdCocobdIsDecsn", EventType.DISCLOSURE, "상각형 조건부자본증권 발행결정", 2),
        e("tsstkAqDecsn", EventType.DISCLOSURE, "자기주식 취득 결정", 3),
        e("tsstkDpDecsn", EventType.DISCLOSURE, "자기주식 처분 결정", 3),
        e("tsstkAqTrctrCnsDecsn", EventType.DISCLOSURE, "자기주식취득 신탁계약 체결 결정", 3),
        e("tsstkAqTrctrCcDecsn", EventType.DISCLOSURE, "자기주식취득 신탁계약 해지 결정", 3),
        e("dfOcr", EventType.RISK, "부도발생", 4),
        e("bsnSp", EventType.RISK, "영업정지", 4),
        e("ctrcvsBgrq", EventType.RISK, "회생절차 개시신청", 4),
        e("dsRsOcr", EventType.RISK, "해산사유 발생", 4),
        e("bnkMngtPcbg", EventType.RISK, "채권은행 등의 관리절차 개시", 4),
        e("lwstLg", EventType.RISK, "소송 등의 제기", 4),
        e("bnkMngtPcsp", EventType.RISK, "채권은행 등의 관리절차 중단", 4),
        e("bsnInhDecsn", EventType.BUSINESS, "영업양수 결정", 5),
        e("bsnTrfDecsn", EventType.BUSINESS, "영업양도 결정", 5),
        e("tgastInhDecsn", EventType.BUSINESS, "유형자산 양수 결정", 5),
        e("tgastTrfDecsn", EventType.BUSINESS, "유형자산 양도 결정", 5),
        e("otcprStkInvscrInhDecsn", EventType.BUSINESS, "타법인 주식 및 출자증권 양수결정", 5),
        e("otcprStkInvscrTrfDecsn", EventType.BUSINESS, "타법인 주식 및 출자증권 양도결정", 5),
        e("stkrtbdInhDecsn", EventType.BUSINESS, "주권 관련 사채권 양수 결정", 5),
        e("stkrtbdTrfDecsn", EventType.BUSINESS, "주권 관련 사채권 양도 결정", 5),
        e("cmpMgDecsn", EventType.BUSINESS, "회사합병 결정", 6),
        e("cmpDvDecsn", EventType.BUSINESS, "회사분할 결정", 6),
        e("cmpDvmgDecsn", EventType.BUSINESS, "회사분할합병 결정", 6),
        e("stkExtrDecsn", EventType.BUSINESS, "주식교환·이전 결정", 6),
        e("ovLstDecsn", EventType.MARKET, "해외 증권시장 주권등 상장 결정", 7),
        e("ovDlstDecsn", EventType.MARKET, "해외 증권시장 주권등 상장폐지 결정", 7),
        e("ovLst", EventType.MARKET, "해외 증권시장 주권등 상장", 7),
        e("ovDlst", EventType.MARKET, "해외 증권시장 주권등 상장폐지", 7));
    public static final String HOLD = "astInhtrfEtcPtbkOpt";
    private OpenDartDs005Catalog() {}
    private static Endpoint e(String key, EventType type, String title, int wave) { return new Endpoint(key, type, title, wave); }
    public static List<Endpoint> automatic() { return AUTOMATIC; }
    public static Endpoint approved(String key) {
        return AUTOMATIC.stream().filter(e -> e.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("DS005 endpoint is not approved for automatic Event registration"));
    }
}
