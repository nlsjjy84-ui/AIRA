package com.aira.api.user.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.user.dto.CurrentUserResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
public class CurrentUserController {
    @GetMapping
    public CurrentUserResponse find(@AuthenticationPrincipal AiraPrincipal principal) {
        return new CurrentUserResponse(principal.userId(), principal.nickname());
    }
}
