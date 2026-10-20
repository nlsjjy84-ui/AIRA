package com.aira.api.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 시장 화면의 환율·금리·원자재. 한국은행 ECOS 통계검색에서 받아 3시간 보관한다. */
@Service
public class MarketIndicatorService {
    private static final Logger log = LoggerFactory.getLogger(MarketIndicatorService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration KEEP = Duration.ofHours(3);

    record Series(String key, String group, String label, String statCode, String itemCode, String cycle, String unit) {}

    static final List<Series> SERIES = List.of(
            new Series("USD", "환율", "원/달러", "731Y001", "0000001", "D", "원"),
            new Series("JPY", "환율", "원/100엔", "731Y001", "0000002", "D", "원"),
            new Series("EUR", "환율", "원/유로", "731Y001", "0000003", "D", "원"),
            new Series("CNY", "환율", "원/위안", "731Y001", "0000053", "D", "원"),
            new Series("BASE_RATE", "금리", "한국은행 기준금리", "722Y001", "0101000", "D", "%"),
            new Series("KTB3", "금리", "국고채 3년", "817Y002", "010200000", "D", "%"),
            new Series("KTB10", "금리", "국고채 10년", "817Y002", "010210000", "D", "%"),
            new Series("CORP3", "금리", "회사채 3년(AA-)", "817Y002", "010300000", "D", "%"),
            new Series("WTI", "원자재", "원유(WTI)", "902Y003", "010101", "M", "달러/배럴"),
            new Series("DUBAI", "원자재", "원유(두바이)", "902Y003", "010102", "M", "달러/배럴"),
            new Series("GOLD", "원자재", "금", "902Y003", "040101", "M", "달러/트로이온스"),
            new Series("COPPER", "원자재", "구리", "902Y003", "040202", "M", "달러/톤"),
            // 예금·대출 금리: 한국은행이 금융기관 보고를 모아 매달 올리는 신규취급액 기준 가중평균 금리
            new Series("SB_DEPOSIT", "예금·대출 금리", "저축은행 정기예금(1년)", "121Y004", "BEBBBE01", "M", "%"),
            new Series("BANK_DEPOSIT", "예금·대출 금리", "은행 정기예금", "121Y002", "BEABAA211", "M", "%"),
            new Series("BANK_LOAN", "예금·대출 금리", "은행 대출(평균)", "121Y006", "BECBLA01", "M", "%"));

    public interface Sender { String get(URI uri) throws IOException, InterruptedException; }

    private final Sender sender;
    private final ObjectMapper json;
    private final String apiKey;
    private final Clock clock;
    private volatile Map.Entry<Instant, Map<String, Object>> cache;

    @Autowired
    public MarketIndicatorService(ObjectMapper json, @Value("${aira.ecos.api-key:}") String apiKey) {
        this(defaultSender(), json, apiKey, Clock.systemUTC());
    }

    MarketIndicatorService(Sender sender, ObjectMapper json, String apiKey, Clock clock) {
        this.sender = sender;
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.clock = clock;
    }

    public Map<String, Object> board() {
        var current = cache;
        if (current != null && current.getKey().plus(KEEP).isAfter(clock.instant())) return current.getValue();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "한국은행 경제통계시스템(ECOS) · 환율·시장금리는 매일, 원자재는 월평균, 예금·대출 금리는 매월(신규취급액 기준)");
        if (apiKey.isBlank()) { out.put("state", "UNAVAILABLE"); out.put("items", List.of()); return out; }
        LocalDate today = LocalDate.now(clock.withZone(KST));
        List<Map<String, Object>> items = new ArrayList<>();
        for (Series s : SERIES) {
            try {
                var points = fetch(s, today);
                if (points.isEmpty()) continue;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("key", s.key());
                item.put("group", s.group());
                item.put("label", s.label());
                item.put("unit", s.unit());
                item.put("cycle", "D".equals(s.cycle()) ? "일별" : "월평균");
                var last = points.get(points.size() - 1);
                item.put("time", last.getKey());
                item.put("value", last.getValue());
                if (points.size() > 1) {
                    var prev = points.get(points.size() - 2);
                    item.put("previousTime", prev.getKey());
                    item.put("previousValue", prev.getValue());
                    item.put("change", last.getValue().subtract(prev.getValue()));
                }
                List<Map<String, Object>> series = new ArrayList<>();
                for (var p : points) series.add(Map.of("time", p.getKey(), "value", p.getValue()));
                item.put("series", series);
                items.add(item);
            } catch (RuntimeException e) {
                log.warn("ECOS indicator {} failed: {}", s.key(), e.getMessage());
            }
        }
        out.put("state", items.isEmpty() ? "UNAVAILABLE" : "AVAILABLE");
        out.put("items", items);
        if (!items.isEmpty()) cache = Map.entry(clock.instant(), out);
        else if (current != null) return current.getValue();
        return out;
    }

    /** 일별은 최근 60일, 월별은 최근 14개월. 값이 빈 날은 건너뛴다. 시간 순서. */
    List<Map.Entry<String, BigDecimal>> fetch(Series s, LocalDate today) {
        String start, end;
        if ("D".equals(s.cycle())) {
            start = today.minusDays(60).format(DateTimeFormatter.BASIC_ISO_DATE);
            end = today.format(DateTimeFormatter.BASIC_ISO_DATE);
        } else {
            start = today.minusMonths(14).format(DateTimeFormatter.ofPattern("yyyyMM"));
            end = today.format(DateTimeFormatter.ofPattern("yyyyMM"));
        }
        URI uri = URI.create("https://ecos.bok.or.kr/api/StatisticSearch/" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                + "/json/kr/1/100/" + s.statCode() + "/" + s.cycle() + "/" + start + "/" + end + "/" + s.itemCode());
        String text;
        try {
            text = sender.get(uri);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ECOS request interrupted");
        } catch (IOException e) {
            throw new IllegalStateException("ECOS request failed");
        }
        JsonNode rows = json.readTree(text).path("StatisticSearch").path("row");
        List<Map.Entry<String, BigDecimal>> out = new ArrayList<>();
        if (!rows.isArray()) return out;
        for (JsonNode row : rows) {
            String time = row.path("TIME").asText("");
            String raw = row.path("DATA_VALUE").asText("").replace(",", "").trim();
            if (time.isBlank() || !raw.matches("-?[0-9]+(\\.[0-9]+)?")) continue;
            out.add(Map.entry(label(time), new BigDecimal(raw)));
        }
        out.sort(Map.Entry.comparingByKey());
        return out;
    }

    static String label(String time) {
        if (time.matches("[0-9]{8}")) return time.substring(0, 4) + "-" + time.substring(4, 6) + "-" + time.substring(6);
        if (time.matches("[0-9]{6}")) return time.substring(0, 4) + "-" + time.substring(4);
        return time;
    }

    static Sender defaultSender() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return uri -> {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            return response.body();
        };
    }
}
