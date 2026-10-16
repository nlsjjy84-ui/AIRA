package com.aira.api.payment;

import com.aira.api.auth.service.DemoLoginService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * AIRA 플러스 결제 흐름: 주문 만들기 → (화면에서 토스 결제창) → 서버에서 금액 확인 후 승인 → 플러스 적용 → 해지·환불.
 *
 * <p>금액은 주문을 만들 때 서버가 정하고, 승인 때 화면이 보낸 금액과 토스가 돌려준 금액이 모두 주문 금액과 같아야만 플러스를 켠다.
 * 공용 데모 계정은 결제 흐름을 끝까지 시험해 볼 수 있지만, 승인 직후 전액 취소하고 플러스는 켜지 않는다(여럿이 함께 쓰는 계정이라서).</p>
 */
@Service
public class PlanPaymentService {
    private static final Logger log = LoggerFactory.getLogger(PlanPaymentService.class);

    public static class Problem extends RuntimeException {
        private final int status;
        private final String code;
        public Problem(int status, String code, String message) { super(message); this.status = status; this.code = code; }
        public int status() { return status; }
        public String code() { return code; }
    }

    private record Order(String orderId, UUID userId, String cycle, int amount, String status, String paymentKey,
            Instant approvedAt) {}

    private final JdbcTemplate jdbc;
    private final TossPaymentsGateway gateway;
    private final Clock clock;

    @Autowired
    public PlanPaymentService(JdbcTemplate jdbc, TossPaymentsGateway gateway) { this(jdbc, gateway, Clock.systemUTC()); }

    PlanPaymentService(JdbcTemplate jdbc, TossPaymentsGateway gateway, Clock clock) {
        this.jdbc = jdbc;
        this.gateway = gateway;
        this.clock = clock;
    }

    public Map<String, Object> config() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", gateway.enabled());
        out.put("testMode", true);
        out.put("clientKey", gateway.enabled() ? gateway.clientKey() : null);
        out.put("prices", PlanPrice.PLUS);
        return out;
    }

    public Map<String, Object> createOrder(UUID userId, String cycle) {
        if (!gateway.enabled()) throw new Problem(503, "PAYMENT_DISABLED", "결제 테스트가 아직 켜져 있지 않습니다.");
        if (!PlanPrice.validCycle(cycle)) throw new Problem(400, "INVALID_CYCLE", "결제 주기는 월 또는 연만 고를 수 있습니다.");
        String orderId = "aira-" + UUID.randomUUID().toString().replace("-", "");
        int amount = PlanPrice.amount(cycle);
        jdbc.update("""
                INSERT INTO payment_order(order_id, user_id, plan, cycle, amount, status, test_mode, created_at)
                VALUES (?, ?, 'PLUS', ?, ?, 'READY', TRUE, ?)
                """, orderId, userId, cycle, amount, Timestamp.from(clock.instant()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderId", orderId);
        out.put("amount", amount);
        out.put("orderName", PlanPrice.orderName(cycle));
        out.put("cycle", cycle);
        return out;
    }

    public Map<String, Object> confirm(UUID userId, boolean demo, String paymentKey, String orderId, Integer amount) {
        if (paymentKey == null || paymentKey.isBlank() || paymentKey.length() > 200 || orderId == null || amount == null) {
            throw new Problem(400, "INVALID_REQUEST", "결제 정보가 올바르지 않습니다.");
        }
        Order order = find(orderId, userId);
        if (order == null) throw new Problem(404, "ORDER_NOT_FOUND", "이 결제 주문을 찾지 못했습니다.");
        if ("DONE".equals(order.status()) && paymentKey.equals(order.paymentKey())) {
            return result(userId, order, false); // 새로 고침으로 같은 승인을 다시 보낸 경우
        }
        if (!"READY".equals(order.status())) throw new Problem(409, "ORDER_CLOSED", "이미 처리된 결제 주문입니다.");
        if (amount != order.amount()) {
            markFailed(orderId);
            throw new Problem(400, "AMOUNT_MISMATCH", "결제 금액이 주문 금액과 달라 승인하지 않았습니다.");
        }
        TossPaymentsGateway.Approved approved;
        try {
            approved = gateway.confirm(paymentKey, orderId, order.amount());
        } catch (TossPaymentsGateway.GatewayException e) {
            markFailed(orderId);
            throw new Problem(502, e.code(), e.getMessage());
        }
        if (!"DONE".equals(approved.status()) || approved.totalAmount() != order.amount() || !orderId.equals(approved.orderId())) {
            markFailed(orderId);
            throw new Problem(502, "APPROVAL_MISMATCH", "결제 승인 결과가 주문과 맞지 않아 플러스를 켜지 않았습니다.");
        }
        Instant now = clock.instant();
        Instant approvedAt = approved.approvedAt() == null ? now : approved.approvedAt();
        jdbc.update("UPDATE payment_order SET status='DONE', payment_key=?, method=?, approved_at=? WHERE order_id=?",
                paymentKey, approved.method(), Timestamp.from(approvedAt), orderId);
        Order done = find(orderId, userId);
        if (demo || DemoLoginService.isDemoUser(userId)) {
            int refunded = gateway.cancel(paymentKey, "공용 데모 계정 결제 시험", order.amount(), orderId + "-demo");
            jdbc.update("UPDATE payment_order SET status='CANCELED', canceled_at=?, refund_amount=? WHERE order_id=?",
                    Timestamp.from(clock.instant()), refunded, orderId);
            return result(userId, find(orderId, userId), true);
        }
        jdbc.update("DELETE FROM user_plan WHERE user_id=?", userId);
        jdbc.update("INSERT INTO user_plan(user_id, plan, cycle, order_id, started_at, ends_at) VALUES (?, 'PLUS', ?, ?, ?, ?)",
                userId, order.cycle(), orderId, Timestamp.from(approvedAt),
                Timestamp.from(PlanPrice.endsAt(approvedAt, order.cycle())));
        log.info("AIRA plus test payment approved for order {}", orderId);
        return result(userId, done, false);
    }

    public Map<String, Object> cancel(UUID userId) {
        Map<String, Object> plan = currentPlan(userId);
        if (!"PLUS".equals(plan.get("plan"))) throw new Problem(404, "NO_PLAN", "해지할 AIRA 플러스가 없습니다.");
        if (Boolean.TRUE.equals(plan.get("canceled"))) throw new Problem(409, "ALREADY_CANCELED", "이미 해지했습니다.");
        Order order = find((String) plan.get("orderId"), userId);
        if (order == null || order.paymentKey() == null) throw new Problem(404, "ORDER_NOT_FOUND", "결제 기록을 찾지 못했습니다.");
        Instant now = clock.instant();
        PlanPrice.Refund refund = PlanPrice.refund(order.cycle(), order.approvedAt() == null ? now : order.approvedAt(), now);
        int refunded = 0;
        if (refund.amount() > 0) {
            try {
                refunded = gateway.cancel(order.paymentKey(), "AIRA 플러스 해지", refund.amount(), order.orderId() + "-cancel");
            } catch (TossPaymentsGateway.GatewayException e) {
                throw new Problem(502, e.code(), e.getMessage());
            }
        }
        jdbc.update("UPDATE payment_order SET status=?, canceled_at=?, refund_amount=? WHERE order_id=?",
                refunded >= order.amount() ? "CANCELED" : "DONE", Timestamp.from(now), refunded, order.orderId());
        if (refund.endsNow()) {
            jdbc.update("UPDATE user_plan SET canceled_at=?, ends_at=? WHERE user_id=?", Timestamp.from(now), Timestamp.from(now), userId);
        } else {
            jdbc.update("UPDATE user_plan SET canceled_at=? WHERE user_id=?", Timestamp.from(now), userId);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("refund", refunded);
        out.put("reason", refund.reason());
        out.put("plan", currentPlan(userId));
        return out;
    }

    /** 지금 쓰는 플랜. 플러스 기간이 끝났으면 무료. */
    public Map<String, Object> currentPlan(UUID userId) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> rows;
        try {
            rows = jdbc.queryForList("SELECT plan, cycle, order_id, started_at, ends_at, canceled_at FROM user_plan WHERE user_id=?", userId);
        } catch (RuntimeException e) {
            rows = List.of();
        }
        Instant now = clock.instant();
        if (!rows.isEmpty()) {
            Map<String, Object> row = rows.get(0);
            Instant ends = instant(value(row, "ends_at"));
            if (ends != null && ends.isAfter(now)) {
                out.put("plan", "PLUS");
                out.put("cycle", value(row, "cycle"));
                out.put("orderId", value(row, "order_id"));
                out.put("startedAt", instant(value(row, "started_at")));
                out.put("endsAt", ends);
                out.put("canceled", value(row, "canceled_at") != null);
                out.put("testMode", true);
                return out;
            }
        }
        out.put("plan", "FREE");
        return out;
    }

    private Map<String, Object> result(UUID userId, Order order, boolean demoCanceled) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", order.status());
        out.put("orderId", order.orderId());
        out.put("amount", order.amount());
        out.put("orderName", PlanPrice.orderName(order.cycle()));
        out.put("approvedAt", order.approvedAt());
        out.put("testMode", true);
        out.put("demoCanceled", demoCanceled);
        out.put("plan", currentPlan(userId));
        return out;
    }

    private void markFailed(String orderId) {
        jdbc.update("UPDATE payment_order SET status='FAILED' WHERE order_id=? AND status='READY'", orderId);
    }

    private Order find(String orderId, UUID userId) {
        if (orderId == null || orderId.length() > 64) return null;
        List<Order> rows = jdbc.query("""
                SELECT order_id, user_id, cycle, amount, status, payment_key, approved_at FROM payment_order
                WHERE order_id=? AND user_id=?
                """, (rs, n) -> new Order(rs.getString(1), rs.getObject(2, UUID.class), rs.getString(3), rs.getInt(4),
                rs.getString(5), rs.getString(6), instant(rs.getObject(7))), orderId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static Object value(Map<String, Object> row, String name) {
        for (Map.Entry<String, Object> entry : row.entrySet()) if (entry.getKey().equalsIgnoreCase(name)) return entry.getValue();
        return null;
    }

    static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Instant i) return i;
        if (value instanceof Timestamp t) return t.toInstant();
        if (value instanceof OffsetDateTime o) return o.toInstant();
        if (value instanceof java.time.ZonedDateTime z) return z.toInstant();
        if (value instanceof java.time.LocalDateTime l) return l.atZone(PlanPrice.KST).toInstant();
        return null;
    }
}
