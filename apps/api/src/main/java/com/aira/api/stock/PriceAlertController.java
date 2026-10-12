package com.aira.api.stock;

import com.aira.api.auth.security.AiraPrincipal;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/price-alerts")
public class PriceAlertController {
    private final PriceAlertService service;

    public PriceAlertController(PriceAlertService service) { this.service = service; }

    public record CreateRequest(UUID companyId, String direction, BigDecimal target) {}

    @GetMapping
    public List<Map<String, Object>> list(@AuthenticationPrincipal AiraPrincipal principal) { return service.list(principal.userId()); }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody CreateRequest request) {
        if (request == null) return ResponseEntity.badRequest().build();
        String error = service.create(principal.userId(), request.companyId(), request.direction(), request.target());
        if ("PLAN_LIMIT".equals(error)) return ResponseEntity.status(429).body(Map.of("code", error));
        if (error != null) return ResponseEntity.badRequest().body(Map.of("code", error));
        return ResponseEntity.ok(Map.of("alerts", service.list(principal.userId())));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AiraPrincipal principal, @PathVariable UUID id) {
        return service.delete(principal.userId(), id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }
}
