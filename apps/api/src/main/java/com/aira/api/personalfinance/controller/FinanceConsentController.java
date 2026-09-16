package com.aira.api.personalfinance.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.personalfinance.dto.FinanceConsentRequest;
import com.aira.api.personalfinance.dto.FinanceConsentResponse;
import com.aira.api.personalfinance.security.FinanceAccessCookieFactory;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.FinanceConsentService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Consent history is itself sensitive, so every operation requires finance step-up access. */
@RestController
@RequestMapping("/api/me/finance/consents")
public class FinanceConsentController {
    private final FinanceAccessGrantService grants;
    private final FinanceConsentService consents;

    public FinanceConsentController(FinanceAccessGrantService grants, FinanceConsentService consents) {
        this.grants = grants;
        this.consents = consents;
    }

    @GetMapping
    public List<FinanceConsentResponse> findAll(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String session,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String grant) {
        grants.requireAccess(principal.userId(), session, grant);
        return consents.findAll(principal.userId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FinanceConsentResponse create(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String session,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String grant,
            @Valid @RequestBody FinanceConsentRequest request) {
        grants.requireAccess(principal.userId(), session, grant);
        return consents.create(principal.userId(), request);
    }

    @DeleteMapping("/{consentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String session,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String grant,
            @PathVariable UUID consentId) {
        grants.requireAccess(principal.userId(), session, grant);
        consents.revoke(principal.userId(), consentId);
    }
}
