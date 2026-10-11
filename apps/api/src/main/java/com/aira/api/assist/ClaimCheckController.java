package com.aira.api.assist;

import com.aira.api.assist.ClaimDtos.ClaimRequest;
import com.aira.api.assist.ClaimDtos.ClaimResponse;
import com.aira.api.auth.security.AiraPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Logged-in users only (/api/me/**). The pasted text is not stored. */
@RestController
@RequestMapping("/api/me/assist")
public class ClaimCheckController {
    private final ClaimCheckService service;

    public ClaimCheckController(ClaimCheckService service) { this.service = service; }

    @PostMapping("/claims")
    public ClaimResponse claims(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody ClaimRequest request) {
        if (request == null || request.text() == null) throw new IllegalArgumentException("Text is required");
        return service.check(principal.userId(), request.text());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void invalid() {}
}
