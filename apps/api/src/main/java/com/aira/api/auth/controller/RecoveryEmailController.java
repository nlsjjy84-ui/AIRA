package com.aira.api.auth.controller;

import com.aira.api.auth.dto.*;
import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.service.RecoveryEmailVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/recovery-email/verifications")
public class RecoveryEmailController {
    private final RecoveryEmailVerificationService service;

    public RecoveryEmailController(RecoveryEmailVerificationService service) { this.service = service; }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void request(@AuthenticationPrincipal AiraPrincipal principal,
            @Valid @RequestBody RecoveryEmailVerificationRequest request) {
        service.request(principal.userId(), request.email());
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(@Valid @RequestBody RecoveryEmailVerificationConfirmRequest request) {
        service.confirm(request.token());
    }
}
