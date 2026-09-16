package com.aira.api.personalfinance.controller;

import com.aira.api.auth.security.AiraPrincipal;
import com.aira.api.auth.security.SessionCookieFactory;
import com.aira.api.personalfinance.dto.BudgetResponse;
import com.aira.api.personalfinance.dto.BudgetUpsertRequest;
import com.aira.api.personalfinance.dto.MonthlySpendingSummaryResponse;
import com.aira.api.personalfinance.security.FinanceAccessCookieFactory;
import com.aira.api.personalfinance.security.FinanceAccessGrantService;
import com.aira.api.personalfinance.service.PersonalFinanceBudgetService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Budget and spending reads stay behind the short-lived finance step-up grant. */
@RestController
@RequestMapping("/api/me/finance")
public class PersonalFinanceBudgetController {
    private final FinanceAccessGrantService grants;
    private final PersonalFinanceBudgetService budgets;

    public PersonalFinanceBudgetController(FinanceAccessGrantService grants,
            PersonalFinanceBudgetService budgets) {
        this.grants = grants;
        this.budgets = budgets;
    }
    @PostMapping("/budgets")
    public BudgetResponse upsert(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String session,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String grant,
            @Valid @RequestBody BudgetUpsertRequest request) {
        grants.requireAccess(principal.userId(), session, grant);
        return budgets.upsert(principal.userId(), request);
    }

    @GetMapping("/budgets")
    public List<BudgetResponse> findBudgets(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String session,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String grant,
            @RequestParam String month,
            @RequestParam String currency) {
        grants.requireAccess(principal.userId(), session, grant);
        return budgets.findBudgets(principal.userId(), month, currency);
    }

    @GetMapping("/summary")
    public MonthlySpendingSummaryResponse summary(
            @AuthenticationPrincipal AiraPrincipal principal,
            @CookieValue(name = SessionCookieFactory.COOKIE_NAME, required = false) String session,
            @CookieValue(name = FinanceAccessCookieFactory.COOKIE_NAME, required = false) String grant,
            @RequestParam String month,
            @RequestParam String currency) {
        grants.requireAccess(principal.userId(), session, grant);
        return budgets.summary(principal.userId(), month, currency);
    }
}
