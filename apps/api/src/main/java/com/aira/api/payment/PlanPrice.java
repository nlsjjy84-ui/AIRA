package com.aira.api.payment;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;

/**
 * AIRA 플러스 가격과 환불 기준. 화면(planQuote.js)과 같은 규칙을 서버에도 둔다.
 * 결제 금액은 언제나 서버가 정한다. 화면이 보낸 금액은 이 값과 맞는지 확인하는 데만 쓴다.
 *
 * <p>환불 기준(정책안): 결제하고 7일 안에 해지하면 전액 환불(전자상거래법 제17조 청약철회 기간).
 * 7일이 지나면 월 결제는 이번 기간이 끝날 때까지 쓰고 끝나며(환불 없음), 연 결제는 쓴 달을 월 요금으로 빼고 돌려준다.</p>
 */
public final class PlanPrice {
    private PlanPrice() {}

    public static final ZoneId KST = ZoneId.of("Asia/Seoul");
    public static final Map<String, Integer> PLUS = Map.of("month", 9900, "year", 99000);

    public record Refund(int amount, String reason, boolean endsNow) {}

    public static boolean validCycle(String cycle) { return "month".equals(cycle) || "year".equals(cycle); }

    public static int amount(String cycle) {
        Integer value = PLUS.get(cycle);
        if (value == null) throw new IllegalArgumentException("cycle must be month or year");
        return value;
    }

    public static String orderName(String cycle) { return "AIRA 플러스 " + ("year".equals(cycle) ? "1년" : "1개월"); }

    /** 이용 기간 끝. 월말에 결제하면 다음 달 마지막 날로 맞춘다(1/31 → 2/28). */
    public static Instant endsAt(Instant start, String cycle) {
        return start.atZone(KST).plusMonths("year".equals(cycle) ? 12 : 1).toInstant();
    }

    public static Refund refund(String cycle, Instant paidAt, Instant cancelAt) {
        int total = amount(cycle);
        long days = Duration.between(paidAt, cancelAt).toDays();
        if (days < 7) return new Refund(total, "결제하고 7일 안에 해지해서 전액 돌려드려요.", true);
        if ("month".equals(cycle)) {
            return new Refund(0, "7일이 지나 해지했어요. 이번 기간이 끝날 때까지 그대로 쓰고, 다음 결제는 하지 않아요.", false);
        }
        ZonedDateTime paid = paidAt.atZone(KST);
        int used = 0;
        while (used < 12 && !paid.plusMonths(used).toInstant().isAfter(cancelAt)) used++;
        int amount = Math.max(0, total - PLUS.get("month") * used);
        return new Refund(amount, "쓴 " + used + "달을 월 요금으로 빼고 나머지를 돌려드려요.", true);
    }
}
