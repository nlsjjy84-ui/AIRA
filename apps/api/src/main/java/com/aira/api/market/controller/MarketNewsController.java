package com.aira.api.market.controller;

import com.aira.api.market.news.MarketNewsFeedResponse;
import com.aira.api.market.news.MarketNewsUnavailableException;
import com.aira.api.market.news.RecentMarketNewsService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MarketNewsController {
    private final RecentMarketNewsService service;

    public MarketNewsController(RecentMarketNewsService service) { this.service = service; }

    @GetMapping("/api/news/latest")
    public MarketNewsFeedResponse latest() { return service.latest(); }

    @ExceptionHandler(MarketNewsUnavailableException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    void unavailable() {}
}
