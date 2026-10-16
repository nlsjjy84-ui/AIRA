package com.aira.api.stock;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 시장 화면의 지수(KRX·코스피·코스닥 시리즈 일별시세)와 국내 금 시세(KRX 금시장 일별매매정보).
 * 거래소는 그날 시세를 다음 영업일 아침에 공개하므로 오늘부터 7일 전까지 거꾸로 찾아 처음 나오는 날을 쓰고, 3시간 보관한다.
 */
@Service
public class KrxBoardService {
    private static final Logger log = LoggerFactory.getLogger(KrxBoardService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Duration KEEP = Duration.ofHours(3);
    static final String BASE = "https://data-dbg.krx.co.kr/svc/apis/";

    /** 화면 이름 → [API 경로, 출처 이름] */
    static final Map<String, String[]> INDEX_SERIES = Map.of(
            "krx", new String[] {"idx/krx_dd_trd", "KRX 시리즈 일별시세정보"},
            "kospi", new String[] {"idx/kospi_dd_trd", "KOSPI 시리즈 일별시세정보"},
            "kosdaq", new String[] {"idx/kosdaq_dd_trd", "KOSDAQ 시리즈 일별시세정보"});
    static final String GOLD = "gen/gold_bydd_trd";

    private final EtfService.Sender sender;
    private final ObjectMapper json;
    private final String key;
    private final Clock clock;
    private final Map<String, Map.Entry<Instant, Map<String, Object>>> cache = new ConcurrentHashMap<>();

    @Autowired
    public KrxBoardService(ObjectMapper json, @Value("${aira.krx.auth-key:}") String key) {
        this(EtfService.defaultSender(), json, key, Clock.systemUTC());
    }

    KrxBoardService(EtfService.Sender sender, ObjectMapper json, String key, Clock clock) {
        this.sender = sender;
        this.json = json;
        this.key = key == null ? "" : key.trim();
        this.clock = clock;
    }

    /** 지수 시리즈 하나(krx · kospi · kosdaq). 모르는 이름이면 krx. */
    public Map<String, Object> indices(String series) {
        String name = series == null || !INDEX_SERIES.containsKey(series) ? "krx" : series;
        String[] spec = INDEX_SERIES.get(name);
        Map<String, Object> out = board(spec[0], "KRX 정보데이터시스템 Open API · " + spec[1], KrxBoardService::indexRow);
        out.put("series", name);
        return out;
    }

    /** 국내 금 현물(1kg · 미니금 100g). 값은 1g당 원. */
    public Map<String, Object> gold() {
        return board(GOLD, "KRX 정보데이터시스템 Open API · 금시장 일별매매정보", KrxBoardService::goldRow);
    }

    private Map<String, Object> board(String path, String source, Function<JsonNode, Map<String, Object>> row) {
        var current = cache.get(path);
        if (current != null && current.getKey().plus(KEEP).isAfter(clock.instant())) return new LinkedHashMap<>(current.getValue());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", source);
        if (key.isBlank()) { out.put("state", "UNAVAILABLE"); out.put("items", List.of()); return out; }
        LocalDate today = LocalDate.now(clock.withZone(KST));
        try {
            for (int back = 0; back <= 7; back++) {
                LocalDate day = today.minusDays(back);
                List<Map<String, Object>> items = fetch(path, day, row);
                if (items.isEmpty()) continue;
                out.put("date", day.toString());
                out.put("state", "AVAILABLE");
                out.put("count", items.size());
                out.put("items", items);
                cache.put(path, Map.entry(clock.instant(), out));
                return new LinkedHashMap<>(out);
            }
            out.put("state", "NO_DATA");
        } catch (RuntimeException e) {
            log.warn("KRX {} failed: {}", path, e.getMessage());
            if (current != null) return new LinkedHashMap<>(current.getValue());
            out.put("state", "UNAVAILABLE");
        }
        out.put("items", List.of());
        return out;
    }

    List<Map<String, Object>> fetch(String path, LocalDate day, Function<JsonNode, Map<String, Object>> row) {
        String text;
        try {
            text = sender.get(URI.create(BASE + path + "?basDd=" + day.format(DateTimeFormatter.BASIC_ISO_DATE)), key);
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
            Map<String, Object> m = row.apply(r);
            if (m != null) out.add(m);
        }
        return out;
    }

    static Map<String, Object> indexRow(JsonNode r) {
        String name = r.path("IDX_NM").asText("").trim();
        BigDecimal close = EtfService.num(r, "CLSPRC_IDX");
        if (name.isEmpty() || close == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("group", r.path("IDX_CLSS").asText("").trim());
        m.put("name", name);
        m.put("close", close);
        m.put("change", EtfService.num(r, "CMPPREVDD_IDX"));
        m.put("changeRate", EtfService.num(r, "FLUC_RT"));
        m.put("open", EtfService.num(r, "OPNPRC_IDX"));
        m.put("high", EtfService.num(r, "HGPRC_IDX"));
        m.put("low", EtfService.num(r, "LWPRC_IDX"));
        m.put("value", EtfService.num(r, "ACC_TRDVAL"));
        m.put("marketCap", EtfService.num(r, "MKTCAP"));
        return m;
    }

    static Map<String, Object> goldRow(JsonNode r) {
        String name = r.path("ISU_NM").asText("").trim();
        BigDecimal close = EtfService.num(r, "TDD_CLSPRC");
        if (name.isEmpty() || close == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", r.path("ISU_CD").asText("").trim());
        m.put("name", name);
        m.put("close", close);
        m.put("change", EtfService.num(r, "CMPPREVDD_PRC"));
        m.put("changeRate", EtfService.num(r, "FLUC_RT"));
        m.put("open", EtfService.num(r, "TDD_OPNPRC"));
        m.put("high", EtfService.num(r, "TDD_HGPRC"));
        m.put("low", EtfService.num(r, "TDD_LWPRC"));
        m.put("volume", EtfService.num(r, "ACC_TRDVOL"));
        m.put("value", EtfService.num(r, "ACC_TRDVAL"));
        return m;
    }
}
