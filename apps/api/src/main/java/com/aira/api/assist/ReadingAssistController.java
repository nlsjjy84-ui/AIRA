package com.aira.api.assist;

import com.aira.api.auth.security.AiraPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Logged-in users only (the /api/me/** boundary). Nothing is stored beyond a server log line. */
@RestController
@RequestMapping("/api/me/assist")
public class ReadingAssistController {
    private final ReadingAssistService service;

    private com.aira.api.stock.PlanLimits limits;

    public ReadingAssistController(ReadingAssistService service) { this.service = service; }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setLimits(com.aira.api.stock.PlanLimits limits) { this.limits = limits; }

    private void spend(java.util.UUID user) {
        if (limits != null && !limits.useAi(user)) throw new com.aira.api.stock.PlanLimits.LimitReached();
    }

    @PostMapping("/summary")
    public AssistResponse summary(@AuthenticationPrincipal AiraPrincipal principal,
            @RequestBody AssistRequest request) {
        spend(principal.userId());
        return service.assist(principal.userId(), request);
    }

    @PostMapping("/route")
    public RouteResponse route(@AuthenticationPrincipal AiraPrincipal principal,
            @RequestBody RouteRequest request) {
        return service.route(principal.userId(), request == null ? null : request.question());
    }

    @PostMapping("/screen")
    public ScreenResponse screen(@AuthenticationPrincipal AiraPrincipal principal,
            @RequestBody RouteRequest request) {
        spend(principal.userId());
        return service.screen(principal.userId(), request == null ? null : request.question());
    }

    @ExceptionHandler(com.aira.api.stock.PlanLimits.LimitReached.class)
    org.springframework.http.ResponseEntity<java.util.Map<String, String>> planLimit() {
        return org.springframework.http.ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(java.util.Map.of("code", "PLAN_LIMIT"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void invalid() {}
}
