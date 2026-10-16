package com.aira.api.assist;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 연간 재무 표(fact) 밖의 공시 값: 분기 실적(분기·반기·3분기·사업보고서 누적 손익에서 계산), 사업보고서의
 * 주당 현금배당금(보통주)과 직원 수. 값이 없으면 null.
 */
public interface ReportFactSource {
    /** quarter: 1~4 또는 null(연간 값). note: 계산 방법처럼 결과에 함께 보여 줄 설명. */
    record Line(int year, Integer quarter, BigDecimal amount, String receiptNumber, String note) {}

    /** year가 null이면 그 분기 값이 있는 가장 최근 해. item: REVENUE, OPERATING_INCOME, NET_INCOME. */
    Line quarterValue(UUID companyId, String item, Integer year, int quarter);

    Line dividendPerShare(UUID companyId, Integer year);

    Line employees(UUID companyId, Integer year);
}
