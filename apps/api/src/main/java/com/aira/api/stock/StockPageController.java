package com.aira.api.stock;

import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 종목 화면(묻다·보다·잇다·가늠하다)이 쓰는 읽기 전용 API. 로그인 없이 볼 수 있다. */
@RestController
@RequestMapping("/api/stocks")
public class StockPageController {
    private final StockPageService service;
    private final ScreenerService screener;

    public StockPageController(StockPageService service, ScreenerService screener) { this.service = service; this.screener = screener; }

    @GetMapping("/screener")
    public Map<String, Object> screener() { return screener.table(); }

    @GetMapping("/{companyId}/overview")
    public ResponseEntity<Map<String, Object>> overview(@PathVariable UUID companyId) {
        return with(companyId, service::overview);
    }

    @GetMapping("/{companyId}/prices")
    public ResponseEntity<Map<String, Object>> prices(@PathVariable UUID companyId, @RequestParam(defaultValue = "370") int days) {
        return with(companyId, company -> service.prices(company, days));
    }

    @GetMapping("/{companyId}/financials")
    public ResponseEntity<Map<String, Object>> financials(@PathVariable UUID companyId) {
        return with(companyId, service::financials);
    }

    @GetMapping("/{companyId}/filings")
    public ResponseEntity<Map<String, Object>> filings(@PathVariable UUID companyId) {
        return with(companyId, service::filings);
    }

    @GetMapping("/{companyId}/report-details")
    public ResponseEntity<Map<String, Object>> reportDetails(@PathVariable UUID companyId) {
        return with(companyId, service::reportDetails);
    }

    @GetMapping("/{companyId}/peers")
    public ResponseEntity<Map<String, Object>> peers(@PathVariable UUID companyId) {
        return with(companyId, service::peers);
    }

    @GetMapping("/{companyId}/quarters")
    public ResponseEntity<Map<String, Object>> quarters(@PathVariable UUID companyId) {
        return with(companyId, service::quarters);
    }

    @GetMapping("/{companyId}/ownership")
    public ResponseEntity<Map<String, Object>> ownership(@PathVariable UUID companyId) {
        return with(companyId, service::ownership);
    }

    @GetMapping("/{companyId}/news")
    public ResponseEntity<Map<String, Object>> news(@PathVariable UUID companyId) {
        return with(companyId, service::news);
    }

    @GetMapping("/market-filings")
    public Map<String, Object> marketFilings() { return service.marketFilings(); }

    @GetMapping("/market-index/{market}")
    public ResponseEntity<Map<String, Object>> index(@PathVariable String market, @RequestParam(defaultValue = "370") int days) {
        if (!"KOSPI".equals(market) && !"KOSDAQ".equals(market)) return ResponseEntity.badRequest().build();
        return ResponseEntity.ok(service.index(market, days));
    }

    private ResponseEntity<Map<String, Object>> with(UUID companyId, Function<StockCompanyLookup.Company, Map<String, Object>> body) {
        return service.company(companyId).map(company -> ResponseEntity.ok(body.apply(company)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
