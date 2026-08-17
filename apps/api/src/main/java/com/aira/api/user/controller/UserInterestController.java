package com.aira.api.user.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.user.dto.UserInterestResponse;
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
}
