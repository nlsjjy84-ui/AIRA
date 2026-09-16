package com.aira.api.personalfinance.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.personalfinance.dto.DemoFinanceImportResponse;
import com.aira.api.personalfinance.security.FinanceAccessCookieFactory;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.DemoFinanceImportService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Imports only clearly labeled AIRA demo finance data; never a live institution feed. */
@RestController
@RequestMapping("/api/me/finance/demo-import")
public class DemoFinanceImportController {
    private final FinanceAccessGrantService grants;
    private final DemoFinanceImportService imports;

    public DemoFinanceImportController(FinanceAccessGrantService grants, DemoFinanceImportService imports) {
        this.grants = grants;
        this.imports = imports;
    }

    @PostMapping
    public DemoFinanceImportResponse importDemo(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String session,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String grant) {
        grants.requireAccess(principal.userId(), session, grant);
        return imports.importDemo(principal.userId());
    }
}
