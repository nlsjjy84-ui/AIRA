package com.aira.api.assist;

import com.aira.api.assist.FilingDtos.CandidateRequest;
import com.aira.api.assist.FilingDtos.CandidateResponse;
import com.aira.api.assist.FilingDtos.CompareRequest;
import com.aira.api.assist.FilingDtos.CompareResponse;
import com.aira.api.assist.FilingDtos.QuoteRequest;
import com.aira.api.assist.FilingDtos.QuoteResponse;
import com.aira.api.auth.security.AiraPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Logged-in users only (/api/me/**). Filing text and questions are never stored. */
@RestController
@RequestMapping("/api/me/assist")
public class FilingAssistController {
    private final FilingQuoteService quotes;
    private final RelationCandidateService candidates;

    public FilingAssistController(FilingQuoteService quotes, RelationCandidateService candidates) {
        this.quotes = quotes;
        this.candidates = candidates;
    }

    @PostMapping("/quote")
    public QuoteResponse quote(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody QuoteRequest request) {
        return quotes.ask(principal.userId(), request);
    }

    @PostMapping("/compare")
    public CompareResponse compare(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody CompareRequest request) {
        return quotes.compare(principal.userId(), request);
    }

    @PostMapping("/candidates")
    public CandidateResponse candidates(@AuthenticationPrincipal AiraPrincipal principal, @RequestBody CandidateRequest request) {
        return candidates.suggest(principal.userId(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    void invalid() {}
}
