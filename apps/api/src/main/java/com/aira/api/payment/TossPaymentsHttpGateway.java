package com.aira.api.payment;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 토스페이먼츠 결제 승인·취소 API(API 개별 연동 키).
 *
 * <p>포트폴리오용이라 <b>테스트 키(test_ 로 시작)만</b> 받는다. 실제 키가 들어오면 켜지지 않아, 실수로 진짜 돈이 오가는 일을 막는다.
 * 시크릿 키는 서버 환경 변수로만 받고, 로그나 응답에 남기지 않는다.</p>
 */
@Component
public class TossPaymentsHttpGateway implements TossPaymentsGateway {
    private static final Logger log = LoggerFactory.getLogger(TossPaymentsHttpGateway.class);
    private static final URI BASE = URI.create("https://api.tosspayments.com/v1/payments/");

    private final String clientKey;
    private final String authorization;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper json = new ObjectMapper();

    public TossPaymentsHttpGateway(@Value("${aira.toss.client-key:}") String clientKey,
            @Value("${aira.toss.secret-key:}") String secretKey) {
        boolean ok = clientKey != null && clientKey.startsWith("test_ck_")
                && secretKey != null && secretKey.startsWith("test_sk_");
        if (!ok && ((clientKey != null && !clientKey.isBlank()) || (secretKey != null && !secretKey.isBlank()))) {
            log.warn("Toss payments disabled: only API 개별 연동 test keys (test_ck_ / test_sk_) are accepted");
        }
        this.clientKey = ok ? clientKey : null;
        this.authorization = ok ? "Basic " + Base64.getEncoder()
                .encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8)) : null;
    }

    @Override
    public boolean enabled() { return clientKey != null; }

    @Override
    public String clientKey() { return clientKey; }

    @Override
    public Approved confirm(String paymentKey, String orderId, int amount) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentKey", paymentKey);
        body.put("orderId", orderId);
        body.put("amount", amount);
        JsonNode node = post(BASE.resolve("confirm"), body, null);
        Instant approvedAt = null;
        String approved = node.path("approvedAt").asText("");
        if (!approved.isBlank()) {
            try { approvedAt = OffsetDateTime.parse(approved).toInstant(); } catch (RuntimeException ignored) { approvedAt = Instant.now(); }
        }
        return new Approved(node.path("paymentKey").asText(""), node.path("orderId").asText(""),
                node.path("totalAmount").asInt(-1), node.path("status").asText(""), node.path("method").asText(""), approvedAt);
    }

    @Override
    public int cancel(String paymentKey, String reason, int amount, String idempotencyKey) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("cancelReason", reason);
        body.put("cancelAmount", amount);
        String path = URLEncoder.encode(paymentKey, StandardCharsets.UTF_8) + "/cancel";
        JsonNode node = post(BASE.resolve(path), body, idempotencyKey);
        JsonNode cancels = node.path("cancels");
        if (cancels.isArray() && !cancels.isEmpty()) return cancels.get(cancels.size() - 1).path("cancelAmount").asInt(amount);
        return amount;
    }

    private JsonNode post(URI uri, Map<String, Object> body, String idempotencyKey) {
        if (!enabled()) throw new GatewayException("DISABLED", "결제 테스트 키가 설정되지 않았습니다.");
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
                    .header("Authorization", authorization)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
            if (idempotencyKey != null) request.header("Idempotency-Key", idempotencyKey);
            HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode node = json.readTree(response.body() == null || response.body().isBlank() ? "{}" : response.body());
            if (response.statusCode() / 100 != 2) {
                String code = node.path("code").asText("PAYMENT_FAILED");
                String message = node.path("message").asText("결제를 처리하지 못했습니다.");
                log.warn("Toss payments {} returned {} {}", uri.getPath(), response.statusCode(), code);
                throw new GatewayException(code, message);
            }
            return node;
        } catch (GatewayException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayException("NETWORK", "결제 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
        } catch (Exception e) {
            log.warn("Toss payments call failed: {}", e.getClass().getSimpleName());
            throw new GatewayException("NETWORK", "결제 서비스에 연결하지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
        }
    }
}
