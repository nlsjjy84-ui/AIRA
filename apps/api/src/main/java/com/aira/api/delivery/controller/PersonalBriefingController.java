package com.aira.api.delivery.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.delivery.dto.BriefingResponse;
import com.aira.api.delivery.service.BriefingNotFoundException;
import com.aira.api.delivery.service.PersonalBriefingService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/briefings")
public class PersonalBriefingController {
    private final PersonalBriefingService service;
    public PersonalBriefingController(PersonalBriefingService service) { this.service = service; }

    @PostMapping("/current")
    public BriefingResponse current(@AuthenticationPrincipal AiraPrincipal principal) {
        return service.getOrCreate(principal.userId());
    }

    @GetMapping("/{briefingId}")
    public BriefingResponse find(@AuthenticationPrincipal AiraPrincipal principal,
            @PathVariable UUID briefingId) {
        return service.findOwned(principal.userId(), briefingId);
    }

    @ExceptionHandler(BriefingNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    void notFound() {}
}
