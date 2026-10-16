package com.aira.api.stock;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 시장 화면의 환율·금리·원자재(한국은행 ECOS). 로그인 없이 볼 수 있다. */
@RestController
public class MarketIndicatorController {
    private final MarketIndicatorService service;

    public MarketIndicatorController(MarketIndicatorService service) { this.service = service; }

    @GetMapping("/api/stocks/market-indicators")
    public Map<String, Object> board() { return service.board(); }
}
