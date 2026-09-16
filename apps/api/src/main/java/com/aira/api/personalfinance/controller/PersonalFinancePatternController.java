package com.aira.api.personalfinance.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.personalfinance.dto.MonthlySpendingPatternResponse;
import com.aira.api.personalfinance.security.FinanceAccessCookieFactory;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.PersonalFinancePatternService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Protected deterministic pattern analysis over user-owned personal-finance data. */
@RestController
@RequestMapping("/api/me/finance/patterns")
public class PersonalFinancePatternController {
    private final FinanceAccessGrantService grants;
    private final PersonalFinancePatternService patterns;

    public PersonalFinancePatternController(FinanceAccessGrantService grants,
            PersonalFinancePatternService patterns) {
        this.grants = grants;
        this.patterns = patterns;
    }

    @GetMapping
    public MonthlySpendingPatternResponse analyze(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String session,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String grant,
            @RequestParam String month,
            @RequestParam String currency) {
        grants.requireAccess(principal.userId(), session, grant);
        return patterns.analyze(principal.userId(), month, currency);
    }
}
