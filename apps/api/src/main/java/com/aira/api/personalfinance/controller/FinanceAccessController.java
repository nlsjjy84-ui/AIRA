package com.aira.api.personalfinance.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.personalfinance.dto.FinanceAccessStatusResponse;
import com.aira.api.personalfinance.dto.FinanceReauthenticationRequest;
import com.aira.api.personalfinance.security.FinanceAccessCookieFactory;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP boundary for finance step-up authentication; it never returns a raw grant token in JSON. */
@RestController
@RequestMapping("/api/me/finance/access")
public class FinanceAccessController {
    private final FinanceAccessGrantService grants;
    private final FinanceAccessCookieFactory cookies;

    public FinanceAccessController(FinanceAccessGrantService grants, FinanceAccessCookieFactory cookies) {
        this.grants = grants;
        this.cookies = cookies;
    }

    @PostMapping("/reauthenticate")
    public ResponseEntity<Void> reauthenticate(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME) String rawSessionToken,
            @Valid @RequestBody FinanceReauthenticationRequest request) {
        String rawGrant = grants.issue(principal.userId(), rawSessionToken, request.password());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.create(rawGrant).toString())
                .build();
    }

    @GetMapping
    public FinanceAccessStatusResponse status(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String rawSessionToken,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String rawGrantToken) {
        return new FinanceAccessStatusResponse(
                grants.hasAccess(principal.userId(), rawSessionToken, rawGrantToken));
    }

    @DeleteMapping
    public ResponseEntity<Void> revoke(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String rawSessionToken) {
        grants.revoke(principal.userId(), rawSessionToken);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.delete().toString())
                .build();
    }
}
