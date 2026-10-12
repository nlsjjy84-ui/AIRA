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

    public ReadingAssistController(ReadingAssistService service) { this.service = service; }

    @PostMapping("/summary")
    public AssistResponse summary(@AuthenticationPrincipal AiraPrincipal principal,
            @RequestBody AssistRequest request) {
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
        return service.screen(principal.userId(), request == null ? null : request.question());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void invalid() {}
}
