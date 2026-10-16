package com.aira.api.payment;

import com.aira.api.auth.security.AiraPrincipal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** AIRA 플러스 테스트 결제(토스페이먼츠 테스트 모드). 실제 돈은 오가지 않는다. */
@RestController
public class PaymentController {
    public record OrderRequest(String cycle) {}
    public record ConfirmRequest(String paymentKey, String orderId, Integer amount) {}

    private final PlanPaymentService service;

    public PaymentController(PlanPaymentService service) { this.service = service; }

    @GetMapping("/api/payments/config")
    public Map<String, Object> config() { return service.config(); }

    @GetMapping("/api/me/payments/plan")
    public Map<String, Object> plan(@AuthenticationPrincipal AiraPrincipal principal) {
        return service.currentPlan(principal.userId());
    }

    @PostMapping("/api/me/payments/orders")
    public Map<String, Object> order(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody OrderRequest request) {
        return service.createOrder(principal.userId(), request == null ? null : request.cycle());
    }

    @PostMapping("/api/me/payments/confirm")
    public Map<String, Object> confirm(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody ConfirmRequest request) {
        if (request == null) throw new PlanPaymentService.Problem(400, "INVALID_REQUEST", "결제 정보가 올바르지 않습니다.");
        boolean demo = com.aira.api.auth.service.DemoLoginService.isDemoNickname(principal.nickname());
        return service.confirm(principal.userId(), demo, request.paymentKey(), request.orderId(), request.amount());
    }

    @PostMapping("/api/me/payments/cancel")
    public Map<String, Object> cancel(@AuthenticationPrincipal AiraPrincipal principal) {
        return service.cancel(principal.userId());
    }

    @ExceptionHandler(PlanPaymentService.Problem.class)
    public ResponseEntity<Map<String, Object>> problem(PlanPaymentService.Problem problem) {
        return ResponseEntity.status(problem.status()).body(Map.of("code", problem.code(), "message", problem.getMessage()));
    }
}
