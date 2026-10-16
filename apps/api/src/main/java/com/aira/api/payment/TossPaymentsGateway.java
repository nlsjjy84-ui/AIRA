package com.aira.api.payment;

import java.time.Instant;

/** 토스페이먼츠 결제 승인·취소. 테스트에서는 가짜로 바꿔 끼운다. */
public interface TossPaymentsGateway {
    record Approved(String paymentKey, String orderId, int totalAmount, String status, String method, Instant approvedAt) {}

    /** 결제 서비스가 거절하거나 응답하지 않을 때. message 는 화면에 그대로 보여도 되는 토스 안내 문구다. */
    class GatewayException extends RuntimeException {
        private final String code;
        public GatewayException(String code, String message) { super(message); this.code = code; }
        public String code() { return code; }
    }

    /** 테스트 키가 두 개 모두 있을 때만 켠다. */
    boolean enabled();

    /** 화면에서 결제창을 여는 데 쓰는 클라이언트 키(공개해도 되는 키). 꺼져 있으면 null. */
    String clientKey();

    Approved confirm(String paymentKey, String orderId, int amount);

    /** 돌려준 금액을 돌려준다. */
    int cancel(String paymentKey, String reason, int amount, String idempotencyKey);
}
