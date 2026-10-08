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
    REAL_GDP,
    OPEN_PRICE, HIGH_PRICE, LOW_PRICE, CLOSE_PRICE,
    TRADING_VOLUME, TRADING_VALUE, MARKET_CAP, LISTED_SHARES,
    INDEX_CLOSE, INDEX_CHANGE, INDEX_CHANGE_RATE;

    /** 기업 연간 공시(OpenDART)에서 가져오는 재무 항목. 금액과 통화가 반드시 함께 저장된다. */
    public static final Set<FactPredicate> COMPANY_FINANCIALS = Collections.unmodifiableSet(
            EnumSet.of(REVENUE, OPERATING_INCOME, NET_INCOME, TOTAL_ASSETS, TOTAL_LIABILITIES, TOTAL_EQUITY));

    /** 처음부터 지원하던 두 항목. 공시에 없으면 수집 전체가 실패한다(기존 동작 유지). */
    public static final Set<FactPredicate> CORE_FINANCIALS = Collections.unmodifiableSet(
            EnumSet.of(REVENUE, OPERATING_INCOME));

    public boolean isCompanyFinancial() {
        return COMPANY_FINANCIALS.contains(this);
    }
}
