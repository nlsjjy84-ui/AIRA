package com.aira.api.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
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
import java.util.Comparator;
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

/**
 * 시장 화면의 ETF 목록. KRX Open API 「ETF 일별매매정보」(etp/etf_bydd_trd)에서 가장 최근 거래일 하루치를 받아 3시간 보관한다.
 * 거래소는 그날 시세를 다음 영업일 아침에 공개하므로 오늘부터 7일 전까지 거꾸로 찾아 처음 나오는 날을 쓴다.
 */
@Service
public class EtfService {
    private static final Logger log = LoggerFactory.getLogger(EtfService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration KEEP = Duration.ofHours(3);
    static final String URL = "https://data-dbg.krx.co.kr/svc/apis/etp/etf_bydd_trd?basDd=";

    public interface Sender { String get(URI uri, String key) throws IOException, InterruptedException; }

    private final Sender sender;
    private final ObjectMapper json;
    private final String key;
    private final Clock clock;
    private volatile Map.Entry<Instant, Map<String, Object>> cache;

    @Autowired
    public EtfService(ObjectMapper json, @Value("${aira.krx.auth-key:}") String key) {
        this(defaultSender(), json, key, Clock.systemUTC());
    }

    EtfService(Sender sender, ObjectMapper json, String key, Clock clock) {
        this.sender = sender;
        this.json = json;
        this.key = key == null ? "" : key.trim();
        this.clock = clock;
    }

    public Map<String, Object> board() {
        var current = cache;
        if (current != null && current.getKey().plus(KEEP).isAfter(clock.instant())) return current.getValue();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "KRX 정보데이터시스템 Open API · ETF 일별매매정보");
        if (key.isBlank()) { out.put("state", "UNAVAILABLE"); out.put("items", List.of()); return out; }
        LocalDate today = LocalDate.now(clock.withZone(KST));
        try {
            for (int back = 0; back <= 7; back++) {
                LocalDate day = today.minusDays(back);
                List<Map<String, Object>> items = fetch(day);
                if (items.isEmpty()) continue;
                items.sort(Comparator.comparing((Map<String, Object> m) -> m.get("value") instanceof BigDecimal b ? b : BigDecimal.ZERO).reversed());
                out.put("date", day.toString());
                out.put("state", "AVAILABLE");
                out.put("count", items.size());
                out.put("items", items);
                cache = Map.entry(clock.instant(), out);
                return out;
            }
            out.put("state", "NO_DATA");
        } catch (RuntimeException e) {
            log.warn("KRX ETF list failed: {}", e.getMessage());
            if (current != null) return current.getValue();
            out.put("state", "UNAVAILABLE");
        }
        out.put("items", List.of());
        return out;
    }

    List<Map<String, Object>> fetch(LocalDate day) {
        String text;
        try {
            text = sender.get(URI.create(URL + day.format(DateTimeFormatter.BASIC_ISO_DATE)), key);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("KRX request interrupted");
        } catch (IOException e) {
            throw new IllegalStateException("KRX request failed");
        }
        JsonNode rows = json.readTree(text).path("OutBlock_1");
        List<Map<String, Object>> out = new ArrayList<>();
        if (!rows.isArray()) return out;
        for (JsonNode r : rows) {
            String code = r.path("ISU_CD").asText("").trim();
            String name = r.path("ISU_NM").asText("").trim();
            if (code.isEmpty() || name.isEmpty()) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", code);
            m.put("name", name);
            m.put("close", num(r, "TDD_CLSPRC"));
            m.put("change", num(r, "CMPPREVDD_PRC"));
            m.put("changeRate", num(r, "FLUC_RT"));
            m.put("nav", num(r, "NAV"));
            m.put("open", num(r, "TDD_OPNPRC"));
            m.put("high", num(r, "TDD_HGPRC"));
            m.put("low", num(r, "TDD_LWPRC"));
            m.put("volume", num(r, "ACC_TRDVOL"));
            m.put("value", num(r, "ACC_TRDVAL"));
            m.put("marketCap", num(r, "MKTCAP"));
            m.put("netAsset", num(r, "INVSTASST_NETASST_TOTAMT"));
            m.put("indexName", r.path("IDX_IND_NM").asText("").trim());
            m.put("indexClose", num(r, "OBJ_STKPRC_IDX"));
            m.put("indexChangeRate", num(r, "FLUC_RT_IDX"));
            // 괴리율: 시장 가격이 순자산가치(NAV)보다 얼마나 비싸게(+)·싸게(-)거래됐는지
            BigDecimal close = (BigDecimal) m.get("close"), nav = (BigDecimal) m.get("nav");
            m.put("premium", close == null || nav == null || nav.signum() == 0 ? null
                    : close.subtract(nav).multiply(BigDecimal.valueOf(100)).divide(nav, 2, RoundingMode.HALF_UP));
            out.add(m);
        }
        return out;
    }

    static BigDecimal num(JsonNode row, String field) {
        String raw = row.path(field).asText("").replace(",", "").trim();
        if (!raw.matches("-?[0-9]+(\\.[0-9]+)?")) return null;
        return new BigDecimal(raw);
    }

    static Sender defaultSender() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return (uri, key) -> {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).header("AUTH_KEY", key).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            return response.body();
        };
    }
}
