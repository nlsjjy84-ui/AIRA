package com.aira.api.stock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** 종목 화면의 각 칸(개요·주가·재무·공시·뉴스)을 만든다. 값마다 어디서 왔는지(출처)를 함께 돌려준다. */
@Service
public class StockPageService {
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration PROFILE_KEEP = Duration.ofDays(7);
    private static final Duration FILINGS_KEEP = Duration.ofHours(1);

    private final StockCompanyLookup companies;
    private final StockPriceStore prices;
    private final FinancialHistoryStore financials;
    private final DartStockClient dart;
    private final CompanyNewsService news;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<String, Map.Entry<Instant, List<Map<String, Object>>>> filingCache = new ConcurrentHashMap<>();

    @Autowired
    public StockPageService(StockCompanyLookup companies, StockPriceStore prices, FinancialHistoryStore financials,
            DartStockClient dart, CompanyNewsService news, JdbcTemplate jdbc, ObjectMapper json) {
        this(companies, prices, financials, dart, news, jdbc, json, Clock.systemUTC());
    }

    StockPageService(StockCompanyLookup companies, StockPriceStore prices, FinancialHistoryStore financials,
            DartStockClient dart, CompanyNewsService news, JdbcTemplate jdbc, ObjectMapper json, Clock clock) {
        this.companies = companies; this.prices = prices; this.financials = financials; this.dart = dart;
        this.news = news; this.jdbc = jdbc; this.json = json; this.clock = clock;
    }

    public Optional<StockCompanyLookup.Company> company(UUID id) { return companies.find(id); }

    // ---------- 개요 ----------
    public Map<String, Object> overview(StockCompanyLookup.Company company) {
        Map<String, String> profile = profile(company.corpCode());
        String shortCode = shortCode(company, profile);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("companyId", company.entityId());
        out.put("name", company.name());
        out.put("corpCode", company.corpCode());
        out.put("shortCode", shortCode);
        out.put("market", market(profile));
        out.put("profileState", profile.isEmpty() ? "UNAVAILABLE" : "AVAILABLE");
        Map<String, Object> info = new LinkedHashMap<>();
        for (var key : List.of("corp_name_eng", "stock_name", "ceo_nm", "est_dt", "acc_mt", "adres", "hm_url", "ir_url", "induty_code", "corp_cls"))
            if (profile.get(key) != null && !profile.get(key).isBlank()) info.put(key, profile.get(key).trim());
        out.put("profile", info);
        out.put("profileSource", "OpenDART 기업개황");
        return out;
    }

    // ---------- 주가 ----------
    public Map<String, Object> prices(StockCompanyLookup.Company company, int days) {
        String shortCode = shortCode(company, profile(company.corpCode()));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "KRX 정보데이터시스템 Open API · 일별 매매정보");
        out.put("shortCode", shortCode);
        if (shortCode == null) { out.put("state", "NO_SECURITY"); out.put("points", List.of()); return out; }
        LocalDate from = LocalDate.now(clock.withZone(KST)).minusDays(Math.max(7, Math.min(days, 400)));
        out.put("points", prices.series(shortCode, from).stream().map(StockPageService::point).toList());
        out.put("state", ((List<?>) out.get("points")).isEmpty() ? "NO_DATA" : "AVAILABLE");
        return out;
    }

    public Map<String, Object> index(String market, int days) {
        LocalDate from = LocalDate.now(clock.withZone(KST)).minusDays(Math.max(7, Math.min(days, 400)));
        var points = prices.series(market, from).stream().map(StockPageService::point).toList();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("market", market);
        out.put("source", "KRX 정보데이터시스템 Open API · 지수 일별 시세");
        out.put("state", points.isEmpty() ? "NO_DATA" : "AVAILABLE");
        out.put("points", points);
        return out;
    }

    private static Map<String, Object> point(StockPriceStore.Row r) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("date", r.date().toString());
        p.put("open", r.open()); p.put("high", r.high()); p.put("low", r.low()); p.put("close", r.close());
        p.put("changeRate", r.changeRate()); p.put("volume", r.volume()); p.put("value", r.value());
        p.put("marketCap", r.marketCap()); p.put("listedShares", r.listedShares());
        return p;
    }

    // ---------- 재무 ----------
    public Map<String, Object> financials(StockCompanyLookup.Company company) {
        Map<Integer, Map<String, Object>> byYear = new TreeMap<>();
        for (var v : financials.forCompany(company.corpCode())) {
            var year = byYear.computeIfAbsent(v.fiscalYear(), y -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("year", y);
                m.put("values", new LinkedHashMap<String, Object>());
                return m;
            });
            @SuppressWarnings("unchecked") var values = (Map<String, Object>) year.get("values");
            values.put(v.account(), v.amount());
            year.put("fsDiv", v.fsDiv());
            year.putIfAbsent("receiptNo", v.receiptNo());
            if (v.reportYear() == v.fiscalYear()) year.put("receiptNo", v.receiptNo());
        }
        List<Map<String, Object>> years = new ArrayList<>(byYear.values());
        for (var year : years) {
            year.put("dartUrl", "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + year.get("receiptNo"));
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "OpenDART 다중회사 주요계정 · 사업보고서 (연결 우선, 없으면 별도)");
        out.put("unit", "원");
        out.put("state", years.isEmpty() ? "NO_DATA" : "AVAILABLE");
        out.put("years", years);
        return out;
    }

    // ---------- 공시 ----------
    public Map<String, Object> filings(StockCompanyLookup.Company company) {
        LocalDate to = LocalDate.now(clock.withZone(KST));
        LocalDate from = to.minusMonths(12);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "OpenDART 공시검색");
        out.put("from", from.toString());
        out.put("to", to.toString());
        var cached = filingCache.get(company.corpCode());
        List<Map<String, Object>> items;
        if (cached != null && cached.getKey().plus(FILINGS_KEEP).isAfter(clock.instant())) {
            items = cached.getValue();
        } else {
            try {
                items = dart.filings(company.corpCode(), from, to).stream().map(StockPageService::filing)
                        .sorted(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("date"))).reversed()
                                .thenComparing(m -> String.valueOf(m.get("receiptNo")), Comparator.reverseOrder()))
                        .toList();
                filingCache.put(company.corpCode(), Map.entry(clock.instant(), items));
            } catch (RuntimeException e) {
                out.put("state", cached != null ? "STALE" : "UNAVAILABLE");
                out.put("items", cached != null ? cached.getValue() : List.of());
                return out;
            }
        }
        out.put("state", items.isEmpty() ? "NO_DATA" : "AVAILABLE");
        out.put("items", items);
        return out;
    }

    private static Map<String, Object> filing(Map<String, String> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        String receipt = row.get("rcept_no");
        String day = row.get("rcept_dt");
        m.put("receiptNo", receipt);
        m.put("date", day != null && day.length() == 8 ? day.substring(0, 4) + "-" + day.substring(4, 6) + "-" + day.substring(6) : day);
        String title = row.get("report_nm") == null ? "" : row.get("report_nm").replaceAll("\\s+", " ").trim();
        m.put("title", title);
        m.put("filer", row.get("flr_nm"));
        m.put("remark", row.get("rm"));
        m.put("category", FilingCategories.of(title));
        m.put("url", "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + receipt);
        return m;
    }

    // ---------- 시장 전체 최근 공시 (첫 화면) ----------
    private volatile Map.Entry<Instant, Map<String, Object>> marketFilingCache;

    public Map<String, Object> marketFilings() {
        var cached = marketFilingCache;
        if (cached != null && cached.getKey().plus(Duration.ofMinutes(20)).isAfter(clock.instant())) return cached.getValue();
        LocalDate to = LocalDate.now(clock.withZone(KST));
        LocalDate from = to.minusDays(6);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "OpenDART 공시검색 (유가증권·코스닥)");
        out.put("from", from.toString());
        out.put("to", to.toString());
        try {
            List<Map<String, String>> rows = new ArrayList<>();
            rows.addAll(dart.marketFilings(from, to, "Y"));
            rows.addAll(dart.marketFilings(from, to, "K"));
            var ids = companies.companyIdsByCorpCode(rows.stream().map(r -> r.get("corp_code"))
                    .filter(c -> c != null && c.matches("[0-9]{8}")).distinct().toList());
            List<Map<String, Object>> items = rows.stream().map(row -> {
                Map<String, Object> item = new LinkedHashMap<>(filing(row));
                item.put("companyName", row.get("corp_name"));
                item.put("market", "Y".equals(row.get("corp_cls")) ? "KOSPI" : "KOSDAQ");
                item.put("companyId", ids.get(row.get("corp_code")));
                return item;
            }).sorted(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("receiptNo"))).reversed()).toList();
            out.put("state", items.isEmpty() ? "NO_DATA" : "AVAILABLE");
            out.put("items", items);
            marketFilingCache = Map.entry(clock.instant(), out);
        } catch (RuntimeException e) {
            if (cached != null) return cached.getValue();
            out.put("state", "UNAVAILABLE");
            out.put("items", List.of());
        }
        return out;
    }

    // ---------- 주주·배당·직원 (사업보고서 주요정보) ----------
    private final Map<String, Map.Entry<Instant, Map<String, Object>>> reportCache = new ConcurrentHashMap<>();

    public Map<String, Object> reportDetails(StockCompanyLookup.Company company) {
        var cached = reportCache.get(company.corpCode());
        if (cached != null && cached.getKey().plus(Duration.ofHours(12)).isAfter(clock.instant())) return cached.getValue();
        int latest = LocalDate.now(clock.withZone(KST)).getYear() - 1;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "OpenDART 정기보고서 주요정보 · 사업보고서");
        try {
            for (int year : List.of(latest, latest - 1)) {
                List<Map<String, String>> dividends = dart.reportSection("alotMatter", company.corpCode(), year);
                List<Map<String, String>> holders = dart.reportSection("hyslrSttus", company.corpCode(), year);
                List<Map<String, String>> employees = dart.reportSection("empSttus", company.corpCode(), year);
                if (dividends.isEmpty() && holders.isEmpty() && employees.isEmpty()) continue;
                out.put("year", year);
                out.put("dividends", dividends);
                out.put("holders", holders);
                out.put("employees", employees);
                String receipt = firstReceipt(dividends, holders, employees);
                out.put("receiptNo", receipt);
                out.put("dartUrl", receipt == null ? null : "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + receipt);
                out.put("state", "AVAILABLE");
                reportCache.put(company.corpCode(), Map.entry(clock.instant(), out));
                return out;
            }
            out.put("state", "NO_DATA");
        } catch (RuntimeException e) {
            if (cached != null) return cached.getValue();
            out.put("state", "UNAVAILABLE");
        }
        return out;
    }

    @SafeVarargs
    private static String firstReceipt(List<Map<String, String>>... lists) {
        for (var list : lists) for (var row : list) {
            String r = row.get("rcept_no");
            if (r != null && r.matches("[0-9]{14}")) return r;
        }
        return null;
    }

    // ---------- 뉴스 ----------
    public Map<String, Object> news(StockCompanyLookup.Company company) {
        var result = news.latest(company.name());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "GDELT DOC 2.0 (기사 제목·링크만, 본문은 원문 사이트에서)");
        out.put("state", result.items().isEmpty() && "AVAILABLE".equals(result.state()) ? "NO_DATA" : result.state());
        out.put("items", result.items());
        out.put("searchUrl", "https://search.naver.com/search.naver?where=news&query="
                + java.net.URLEncoder.encode(company.name(), java.nio.charset.StandardCharsets.UTF_8));
        return out;
    }

    // ---------- 공통 ----------
    Map<String, String> profile(String corpCode) {
        try {
            var rows = jdbc.query("SELECT payload, fetched_at FROM company_profile_cache WHERE corp_code=?",
                    (rs, i) -> Map.entry(rs.getString(1), rs.getTimestamp(2).toInstant()), corpCode);
            if (!rows.isEmpty() && rows.get(0).getValue().plus(PROFILE_KEEP).isAfter(clock.instant())) {
                return json.readValue(rows.get(0).getKey(), new TypeReference<Map<String, String>>() {});
            }
            if (!dart.configured()) return rows.isEmpty() ? Map.of() : json.readValue(rows.get(0).getKey(), new TypeReference<Map<String, String>>() {});
            Map<String, String> fresh = dart.company(corpCode);
            if (fresh.isEmpty()) return Map.of();
            jdbc.update("DELETE FROM company_profile_cache WHERE corp_code=?", corpCode);
            jdbc.update("INSERT INTO company_profile_cache(corp_code,payload,fetched_at) VALUES(?,?,?)",
                    corpCode, json.writeValueAsString(fresh), Timestamp.from(clock.instant()));
            return fresh;
        } catch (RuntimeException e) {
            return Map.of();
        }
    }

    String shortCode(StockCompanyLookup.Company company, Map<String, String> profile) {
        String code = profile.get("stock_code");
        if (code != null && code.trim().matches("[0-9A-Z]{6}")) return code.trim();
        return companies.shortCodeByName(company.name()).orElse(null);
    }

    static String market(Map<String, String> profile) {
        String cls = profile.get("corp_cls");
        if ("Y".equals(cls)) return "KOSPI";
        if ("K".equals(cls)) return "KOSDAQ";
        if ("N".equals(cls)) return "KONEX";
        return null;
    }

    static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) return null;
        return numerator.multiply(BigDecimal.valueOf(100)).divide(denominator, 2, RoundingMode.HALF_UP);
    }
}
