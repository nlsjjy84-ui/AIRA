package com.aira.api.market.controller;

import com.aira.api.market.dto.MarketIndexBoardResponse;
import com.aira.api.market.query.MarketIndexQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MarketIndexController {
    private final MarketIndexQuery query;

    public MarketIndexController(MarketIndexQuery query) { this.query = query; }

    @GetMapping("/api/market-indices/latest")
    public MarketIndexBoardResponse latest() { return query.latest(); }
}
