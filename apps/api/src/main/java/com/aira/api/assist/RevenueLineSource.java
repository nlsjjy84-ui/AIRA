package com.aira.api.assist;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 은행·보험·증권·금융지주처럼 '매출' 줄이 없는 회사의 손익계산서에서, 회사가 적은 이름 그대로
 * '영업수익'·'보험수익'·'이자수익' 줄을 찾아 준다(이름을 짐작해 바꾸지 않는다). 찾지 못하면 null.
 */
public interface RevenueLineSource {
    record Line(int year, String accountName, BigDecimal amount, String receiptNumber) {}

    Line revenueLine(UUID companyId, Integer year);
}
