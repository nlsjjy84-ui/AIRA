package com.aira.api.delivery.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.delivery.dto.AlertResponse;
import com.aira.api.delivery.dto.AlertResponse.Item;
import com.aira.api.delivery.service.BriefingNotFoundException;
import com.aira.api.delivery.service.InAppAlertService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/alerts")
public class InAppAlertController {
    private final InAppAlertService service;
    public InAppAlertController(InAppAlertService service){this.service=service;}
    @PostMapping("/reconcile") public AlertResponse reconcile(@AuthenticationPrincipal AiraPrincipal p){return service.reconcile(p.userId());}
    @GetMapping public AlertResponse all(@AuthenticationPrincipal AiraPrincipal p){return service.findAll(p.userId());}
    @GetMapping("/{id}") public Item one(@AuthenticationPrincipal AiraPrincipal p,@PathVariable UUID id){return service.findOwned(p.userId(),id);}
    @ExceptionHandler(BriefingNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND) void missing(){}
}
