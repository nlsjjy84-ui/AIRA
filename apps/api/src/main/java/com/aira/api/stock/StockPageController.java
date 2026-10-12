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
    private final StockHistoryBackfill backfill;

    public StockPageController(StockPageService service, ScreenerService screener, StockHistoryBackfill backfill) {
        this.service = service; this.screener = screener; this.backfill = backfill;
    }

    /** 운영 확인용: 자료 채우기 진행 상황(개수·마지막 오류). 개인정보는 없다. */
    @GetMapping("/backfill-status")
    public Map<String, Object> backfillStatus() { return backfill.status(); }

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

    @GetMapping("/{companyId}/cash-flow")
    public ResponseEntity<Map<String, Object>> cashFlow(@PathVariable UUID companyId) {
        return with(companyId, service::cashFlow);
    }

    @GetMapping("/{companyId}/report-section/{section}")
    public ResponseEntity<Map<String, Object>> reportSection(@PathVariable UUID companyId, @PathVariable String section) {
        if (!StockPageService.SECTIONS.containsKey(section)) return ResponseEntity.badRequest().build();
        return with(companyId, company -> service.reportSection(company, section));
    }

    @GetMapping("/{companyId}/statements")
    public ResponseEntity<Map<String, Object>> statements(@PathVariable UUID companyId) {
        return with(companyId, service::statements);
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
