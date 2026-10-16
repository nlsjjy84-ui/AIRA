package com.aira.api.stock;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 시장 화면의 환율·금리·원자재(한국은행 ECOS)와 ETF(KRX). 로그인 없이 볼 수 있다. */
@RestController
public class MarketIndicatorController {
    private final MarketIndicatorService service;
    private final EtfService etfs;
    private final KrxBoardService krx;

    public MarketIndicatorController(MarketIndicatorService service, EtfService etfs, KrxBoardService krx) { this.service = service; this.etfs = etfs; this.krx = krx; }

    /** 지수(KRX · 코스피 · 코스닥 시리즈 일별시세, 가장 최근 거래일). */
    @GetMapping("/api/stocks/indices")
    public Map<String, Object> indices(@RequestParam(defaultValue = "krx") String series) { return krx.indices(series); }

    /** 국내 금 시세(KRX 금시장 일별매매정보). */
    @GetMapping("/api/stocks/gold")
    public Map<String, Object> gold() { return krx.gold(); }

    @GetMapping("/api/stocks/market-indicators")
    public Map<String, Object> board() { return service.board(); }

    /** ETF 목록(KRX ETF 일별매매정보, 가장 최근 거래일). */
    @GetMapping("/api/stocks/etfs")
    public Map<String, Object> etfs() { return etfs.board(); }
}
