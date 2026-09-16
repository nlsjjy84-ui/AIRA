package com.aira.api.personalfinance.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.personalfinance.security.FinanceAccessCookieFactory;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.PersonalFinanceDataDeletionService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Destructive finance-data actions require the short-lived step-up capability. */
@RestController
@RequestMapping("/api/me/finance/data")
public class PersonalFinanceDataController {
    private final FinanceAccessGrantService grants;
    private final PersonalFinanceDataDeletionService deletion;
    private final FinanceAccessCookieFactory cookies;

    public PersonalFinanceDataController(FinanceAccessGrantService grants,
            PersonalFinanceDataDeletionService deletion, FinanceAccessCookieFactory cookies) {
        this.grants = grants;
        this.deletion = deletion;
        this.cookies = cookies;
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAll(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String session,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String grant) {
        grants.requireAccess(principal.userId(), session, grant);
        deletion.deleteAll(principal.userId());
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.delete().toString())
                .build();
    }
}
