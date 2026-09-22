package com.aira.api.market.controller;

import com.aira.api.market.dto.EconomicIndicatorResponse;
import com.aira.api.market.query.RealGdpIndicatorQuery;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EconomicIndicatorController {
    private final RealGdpIndicatorQuery query;

    public EconomicIndicatorController(RealGdpIndicatorQuery query) { this.query = query; }

    @GetMapping("/api/economic-indicators/real-gdp/latest")
    public EconomicIndicatorResponse latestRealGdp() { return query.latest(); }
}
