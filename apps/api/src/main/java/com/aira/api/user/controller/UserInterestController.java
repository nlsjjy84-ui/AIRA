package com.aira.api.user.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.user.dto.UserInterestResponse;
import com.aira.api.user.dto.InterestEligibilityResponse;
import com.aira.api.user.service.UserInterestService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/interests")
public class UserInterestController {
    private final UserInterestService service;

    public UserInterestController(UserInterestService service) {
        this.service = service;
    }

    @GetMapping
    public List<UserInterestResponse> findAll(@AuthenticationPrincipal AiraPrincipal principal) {
        return service.findAll(principal.userId());
    }

    @GetMapping("/{entityId}/eligibility")
    public InterestEligibilityResponse eligibility(@AuthenticationPrincipal AiraPrincipal principal,
            @PathVariable UUID entityId) {
        return service.eligibility(principal.userId(), entityId);
    }

    @PostMapping("/{entityId}")
    @ResponseStatus(HttpStatus.CREATED)
    public UserInterestResponse add(@AuthenticationPrincipal AiraPrincipal principal,
            @PathVariable UUID entityId) {
        return service.add(principal.userId(), entityId);
    }

    @DeleteMapping("/{entityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal AiraPrincipal principal,
            @PathVariable UUID entityId) {
        service.remove(principal.userId(), entityId);
    }

    @PostMapping("/{entityId}/alert")
    public UserInterestResponse enableAlert(@AuthenticationPrincipal AiraPrincipal principal,
            @PathVariable UUID entityId) {
        return service.setAlertEnabled(principal.userId(), entityId, true);
    }

    @DeleteMapping("/{entityId}/alert")
    public UserInterestResponse disableAlert(@AuthenticationPrincipal AiraPrincipal principal,
            @PathVariable UUID entityId) {
        return service.setAlertEnabled(principal.userId(), entityId, false);
    }
}
