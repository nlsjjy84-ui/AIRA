package com.aira.api.personalfinance.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.personalfinance.dto.PersonalFinanceExplanationResponse;
import com.aira.api.personalfinance.security.FinanceAccessCookieFactory;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.PersonalFinanceAiExplanationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Cost-bearing AI explanation endpoint; finance step-up access is always required. */
@RestController
@RequestMapping("/api/me/finance/ai")
public class PersonalFinanceAiController {
    private final FinanceAccessGrantService grants;
    private final PersonalFinanceAiExplanationService explanations;

    public PersonalFinanceAiController(FinanceAccessGrantService grants,
            PersonalFinanceAiExplanationService explanations) {
        this.grants = grants;
        this.explanations = explanations;
    }

    @PostMapping("/explanation")
    public PersonalFinanceExplanationResponse explain(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String session,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String grant,
            @RequestParam String month,
            @RequestParam String currency) {
        grants.requireAccess(principal.userId(), session, grant);
        return explanations.explain(principal.userId(), month, currency);
    }
}
