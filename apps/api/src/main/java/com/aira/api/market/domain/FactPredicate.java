package com.aira.api.market.domain;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

public enum FactPredicate {
    REVENUE,
    OPERATING_INCOME,
    NET_INCOME,
    TOTAL_ASSETS,
    TOTAL_LIABILITIES,
    TOTAL_EQUITY,
    CURRENT_ASSETS,
    CURRENT_LIABILITIES,
    CASH_AND_EQUIVALENTS,
    OPERATING_CASH_FLOW,
    INVESTING_CASH_FLOW,
    FINANCING_CASH_FLOW,
    REAL_GDP,
    OPEN_PRICE, HIGH_PRICE, LOW_PRICE, CLOSE_PRICE,
    TRADING_VOLUME, TRADING_VALUE, MARKET_CAP, LISTED_SHARES,
    INDEX_CLOSE, INDEX_CHANGE, INDEX_CHANGE_RATE;

    /** 기업 연간 공시(OpenDART)에서 가져오는 재무 항목. 금액과 통화가 반드시 함께 저장된다. */
    public static final Set<FactPredicate> COMPANY_FINANCIALS = Collections.unmodifiableSet(
            EnumSet.of(REVENUE, OPERATING_INCOME, NET_INCOME, TOTAL_ASSETS, TOTAL_LIABILITIES, TOTAL_EQUITY,
                    CURRENT_ASSETS, CURRENT_LIABILITIES, CASH_AND_EQUIVALENTS,
                    OPERATING_CASH_FLOW, INVESTING_CASH_FLOW, FINANCING_CASH_FLOW));

    /**
     * 2026-10 추가 항목: 유동자산·유동부채·현금성자산(재무상태표)과 영업·투자·재무활동 현금흐름(현금흐름표).
     * 금융회사처럼 표에 없는 회사가 있어 선택 항목으로 다룬다(없으면 이 항목만 건너뛴다).
     */
    public static final Set<FactPredicate> EXTENDED_FINANCIALS = Collections.unmodifiableSet(
            EnumSet.of(CURRENT_ASSETS, CURRENT_LIABILITIES, CASH_AND_EQUIVALENTS,
                    OPERATING_CASH_FLOW, INVESTING_CASH_FLOW, FINANCING_CASH_FLOW));

    /** 회사 한 곳을 공시에서 가져올 때 요청하는 항목 순서. 앞의 두 항목만 필수다. */
    public static final java.util.List<FactPredicate> COLLECTED_COMPANY_FINANCIALS = java.util.List.of(
            REVENUE, OPERATING_INCOME, NET_INCOME, TOTAL_ASSETS, TOTAL_LIABILITIES, TOTAL_EQUITY,
            CURRENT_ASSETS, CURRENT_LIABILITIES, CASH_AND_EQUIVALENTS,
            OPERATING_CASH_FLOW, INVESTING_CASH_FLOW, FINANCING_CASH_FLOW);

    /** 처음부터 지원하던 두 항목. 공시에 없으면 수집 전체가 실패한다(기존 동작 유지). */
    public static final Set<FactPredicate> CORE_FINANCIALS = Collections.unmodifiableSet(
            EnumSet.of(REVENUE, OPERATING_INCOME));

    public boolean isCompanyFinancial() {
        return COMPANY_FINANCIALS.contains(this);
    }
}
