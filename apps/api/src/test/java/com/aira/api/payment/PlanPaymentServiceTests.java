package com.aira.api.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aira.api.stock.StockSchema;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** AIRA 플러스 테스트 결제: 금액은 서버가 정하고, 승인 결과가 주문과 맞을 때만 플러스를 켠다. */
class PlanPaymentServiceTests {
    private static final Instant NOW = Instant.parse("2026-10-16T03:00:00Z");

    private static final class FakeGateway implements TossPaymentsGateway {
        boolean enabled = true;
        int approvedAmount = -1; // -1: 요청 금액 그대로 승인
        final List<String> calls = new ArrayList<>();
        public boolean enabled() { return enabled; }
        public String clientKey() { return enabled ? "test_ck_fake" : null; }
        public Approved confirm(String paymentKey, String orderId, int amount) {
            calls.add("confirm " + amount);
            if (paymentKey.equals("declined")) throw new GatewayException("REJECT_CARD_PAYMENT", "한도가 초과됐어요.");
            return new Approved(paymentKey, orderId, approvedAmount < 0 ? amount : approvedAmount, "DONE", "카드", NOW);
        }
        public int cancel(String paymentKey, String reason, int amount, String idempotencyKey) {
            calls.add("cancel " + amount);
            return amount;
        }
    }

    private static JdbcTemplate db() {
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(
                "jdbc:h2:mem:pay-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
        new StockSchema(jdbc).ensure();
        return jdbc;
    }

    private static PlanPaymentService service(JdbcTemplate jdbc, FakeGateway gateway, Instant now) {
        return new PlanPaymentService(jdbc, gateway, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void serverSetsTheAmountAndPlusStartsOnlyAfterAMatchingApproval() {
        var jdbc = db();
        var gateway = new FakeGateway();
        var s = service(jdbc, gateway, NOW);
        UUID me = UUID.randomUUID();
        var order = s.createOrder(me, "year");
        assertEquals(99000, order.get("amount"));
        assertEquals("AIRA 플러스 1년", order.get("orderName"));
        assertEquals("FREE", s.currentPlan(me).get("plan"));

        var done = s.confirm(me, false, "pk_1", (String) order.get("orderId"), 99000);
        assertEquals("DONE", done.get("status"));
        @SuppressWarnings("unchecked") var plan = (Map<String, Object>) done.get("plan");
        assertEquals("PLUS", plan.get("plan"));
        assertEquals(Instant.parse("2027-10-16T03:00:00Z"), plan.get("endsAt"));
        // 새로 고침으로 같은 승인을 다시 보내도 두 번 결제되지 않는다.
        s.confirm(me, false, "pk_1", (String) order.get("orderId"), 99000);
        assertEquals(List.of("confirm 99000"), gateway.calls);
    }

    @Test
    void aTamperedAmountIsRejectedBeforeAskingToss() {
        var jdbc = db();
        var gateway = new FakeGateway();
        var s = service(jdbc, gateway, NOW);
        UUID me = UUID.randomUUID();
        String orderId = (String) s.createOrder(me, "month").get("orderId");
        var e = assertThrows(PlanPaymentService.Problem.class, () -> s.confirm(me, false, "pk_2", orderId, 100));
        assertEquals("AMOUNT_MISMATCH", e.code());
        assertTrue(gateway.calls.isEmpty());
        assertEquals("FREE", s.currentPlan(me).get("plan"));
    }

    @Test
    void approvalWithADifferentAmountDoesNotTurnOnPlus() {
        var jdbc = db();
        var gateway = new FakeGateway();
        gateway.approvedAmount = 100;
        var s = service(jdbc, gateway, NOW);
        UUID me = UUID.randomUUID();
        String orderId = (String) s.createOrder(me, "month").get("orderId");
        assertEquals("APPROVAL_MISMATCH", assertThrows(PlanPaymentService.Problem.class,
                () -> s.confirm(me, false, "pk_3", orderId, 9900)).code());
        assertEquals("FREE", s.currentPlan(me).get("plan"));
    }

    @Test
    void someoneElsesOrderCannotBeConfirmed() {
        var jdbc = db();
        var s = service(jdbc, new FakeGateway(), NOW);
        String orderId = (String) s.createOrder(UUID.randomUUID(), "month").get("orderId");
        assertEquals(404, assertThrows(PlanPaymentService.Problem.class,
                () -> s.confirm(UUID.randomUUID(), false, "pk_4", orderId, 9900)).status());
    }

    @Test
    void aDeclinedCardShowsTossMessageAndKeepsTheFreePlan() {
        var jdbc = db();
        var s = service(jdbc, new FakeGateway(), NOW);
        UUID me = UUID.randomUUID();
        String orderId = (String) s.createOrder(me, "month").get("orderId");
        var e = assertThrows(PlanPaymentService.Problem.class, () -> s.confirm(me, false, "declined", orderId, 9900));
        assertEquals(502, e.status());
        assertEquals("한도가 초과됐어요.", e.getMessage());
        assertEquals("FREE", s.currentPlan(me).get("plan"));
    }

    @Test
    void theSharedDemoAccountCanTryTheWholeFlowButIsRefundedRightAway() {
        var jdbc = db();
        var gateway = new FakeGateway();
        var s = service(jdbc, gateway, NOW);
        UUID demo = UUID.randomUUID();
        String orderId = (String) s.createOrder(demo, "month").get("orderId");
        var result = s.confirm(demo, true, "pk_5", orderId, 9900);
        assertEquals(true, result.get("demoCanceled"));
        assertEquals("CANCELED", result.get("status"));
        assertEquals(List.of("confirm 9900", "cancel 9900"), gateway.calls);
        assertEquals("FREE", s.currentPlan(demo).get("plan"));
    }

    @Test
    void cancellingWithinSevenDaysRefundsEverythingAndEndsPlus() {
        var jdbc = db();
        var gateway = new FakeGateway();
        UUID me = UUID.randomUUID();
        var s = service(jdbc, gateway, NOW);
        String orderId = (String) s.createOrder(me, "year").get("orderId");
        s.confirm(me, false, "pk_6", orderId, 99000);
        var later = service(jdbc, gateway, NOW.plusSeconds(3 * 86400));
        var out = later.cancel(me);
        assertEquals(99000, out.get("refund"));
        @SuppressWarnings("unchecked") var plan = (Map<String, Object>) out.get("plan");
        assertEquals("FREE", plan.get("plan"));
    }

    @Test
    void monthlyCancelAfterSevenDaysKeepsTheRestOfTheMonthWithoutRefund() {
        var jdbc = db();
        var gateway = new FakeGateway();
        UUID me = UUID.randomUUID();
        var s = service(jdbc, gateway, NOW);
        String orderId = (String) s.createOrder(me, "month").get("orderId");
        s.confirm(me, false, "pk_7", orderId, 9900);
        var later = service(jdbc, gateway, NOW.plusSeconds(10 * 86400));
        var out = later.cancel(me);
        assertEquals(0, out.get("refund"));
        @SuppressWarnings("unchecked") var plan = (Map<String, Object>) out.get("plan");
        assertEquals("PLUS", plan.get("plan"));
        assertEquals(true, plan.get("canceled"));
        assertFalse(gateway.calls.contains("cancel 0"));
        assertEquals("ALREADY_CANCELED", assertThrows(PlanPaymentService.Problem.class, () -> later.cancel(me)).code());
    }

    @Test
    void ordersAreRefusedWhileTestKeysAreMissing() {
        var gateway = new FakeGateway();
        gateway.enabled = false;
        var s = service(db(), gateway, NOW);
        assertEquals(503, assertThrows(PlanPaymentService.Problem.class, () -> s.createOrder(UUID.randomUUID(), "month")).status());
        assertEquals(false, s.config().get("enabled"));
    }

    @Test
    void yearlyRefundAfterSevenDaysSubtractsUsedMonthsLikeTheScreen() {
        // 화면 예시와 같은 값: 1월 1일 결제 → 3월 10일 해지 → 3달 사용 → 69,300원 환불
        var refund = PlanPrice.refund("year", Instant.parse("2026-01-01T01:00:00Z"), Instant.parse("2026-03-10T01:00:00Z"));
        assertEquals(69300, refund.amount());
        assertTrue(refund.endsNow());
        assertEquals(99000, PlanPrice.refund("year", NOW, NOW.plusSeconds(6 * 86400)).amount());
    }
}
