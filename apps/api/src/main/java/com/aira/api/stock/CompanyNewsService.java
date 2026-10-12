package com.aira.api.stock;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 회사 이름으로 최근 기사 링크를 찾는다. 네이버 API HUB 뉴스 검색 키가 있으면 그것을 먼저 쓰고(하루 25,000회, 최신순),
 * 없거나 실패하면 GDELT DOC 2.0으로 넘어간다. GDELT는 5초에 한 번만 받으므로 서버 전체에서 6초 간격을 지키고,
 * 회사별 결과를 3시간 저장해 같은 회사는 다시 묻지 않는다. 기사 본문은 받지 않고 제목·링크·매체·시각만 쓴다.
 */
@Service
public class CompanyNewsService {
    static final String ENDPOINT = "https://api.gdeltproject.org/api/v2/doc/doc";
    private static final Duration KEEP = Duration.ofHours(3);
    private static final Duration GAP = Duration.ofSeconds(6);
    private static final DateTimeFormatter SEEN = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");

    public interface Sender { HttpResponse<String> send(URI uri) throws IOException, InterruptedException; }

    static final String NAVER_ENDPOINT = "https://naverapihub.apigw.ntruss.com/search/v1/news";
    static final String NAVER = "NAVER_API_HUB";
    static final String GDELT = "GDELT";

    public interface HeaderSender { HttpResponse<String> send(URI uri, Map<String, String> headers) throws IOException, InterruptedException; }

    public record Article(String title, String url, String domain, OffsetDateTime seenAt, String summary) {
        public Article(String title, String url, String domain, OffsetDateTime seenAt) { this(title, url, domain, seenAt, null); }
    }
    public record Result(String state, List<Article> items, Instant fetchedAt, String provider) {
        public Result(String state, List<Article> items, Instant fetchedAt) { this(state, items, fetchedAt, GDELT); }
    }

    private final Sender sender;
    private final HeaderSender naverSender;
    private final String naverKeyId;
    private final String naverKey;
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<String, Result> cache = new ConcurrentHashMap<>();
    private Instant lastCall = Instant.EPOCH;

    @Autowired
    public CompanyNewsService(ObjectMapper json,
            @Value("${aira.news.naver.key-id:${NAVER_API_HUB_KEY_ID:}}") String naverKeyId,
            @Value("${aira.news.naver.key:${NAVER_API_HUB_KEY:}}") String naverKey) {
        this(defaultSender(), defaultHeaderSender(), naverKeyId, naverKey, json, Clock.systemUTC());
    }

    CompanyNewsService(Sender sender, ObjectMapper json, Clock clock) { this(sender, null, null, null, json, clock); }

    CompanyNewsService(Sender sender, HeaderSender naverSender, String naverKeyId, String naverKey, ObjectMapper json, Clock clock) {
        this.sender = sender; this.naverSender = naverSender; this.json = json; this.clock = clock;
        this.naverKeyId = naverKeyId == null ? "" : naverKeyId.trim();
        this.naverKey = naverKey == null ? "" : naverKey.trim();
    }

    public boolean naverConfigured() { return naverSender != null && !naverKeyId.isEmpty() && !naverKey.isEmpty(); }

    public Result latest(String companyName) {
        Instant now = clock.instant();
        Result cached = cache.get(companyName);
        if (cached != null && cached.fetchedAt().plus(KEEP).isAfter(now)) return cached;
        if (naverConfigured()) {
            Result naver = fromNaver(companyName, now);
            if (naver != null) { cache.put(companyName, naver); return naver; }
        }
        synchronized (this) {
            if (lastCall.plus(GAP).isAfter(now)) {
                return cached != null ? cached : new Result("BUSY", List.of(), now);
            }
            lastCall = now;
        }
        try {
            HttpResponse<String> response = sender.send(uri(companyName));
            if (response.statusCode() != 200) {
                return cached != null ? cached : new Result("UNAVAILABLE", List.of(), now);
            }
            Result fresh = new Result("AVAILABLE", parse(response.body()), now);
            cache.put(companyName, fresh);
            return fresh;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return cached != null ? cached : new Result("UNAVAILABLE", List.of(), now);
        } catch (IOException | RuntimeException e) {
            return cached != null ? cached : new Result("UNAVAILABLE", List.of(), now);
        }
    }

    private Result fromNaver(String companyName, Instant now) {
        try {
            HttpResponse<String> response = naverSender.send(naverUri(companyName),
                    Map.of("X-NCP-APIGW-API-KEY-ID", naverKeyId, "X-NCP-APIGW-API-KEY", naverKey));
            if (response.statusCode() != 200) return null;
            return new Result("AVAILABLE", parseNaver(response.body(), companyName), now, NAVER);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    static URI naverUri(String companyName) {
        return URI.create(NAVER_ENDPOINT + "?query=" + URLEncoder.encode(companyName, StandardCharsets.UTF_8).replace("+", "%20")
                + "&display=30&sort=date&format=json");
    }

    /** 제목에 회사 이름이 들어간 기사만 남긴다(본문에만 한 번 스친 기사는 뺀다). 같은 기사는 한 번만. */
    List<Article> parseNaver(String body, String companyName) {
        if (body == null || !body.trim().startsWith("{")) return List.of();
        JsonNode items = json.readTree(body).get("items");
        if (items == null || !items.isArray()) return List.of();
        Map<String, Article> unique = new LinkedHashMap<>();
        Map<String, Article> loose = new LinkedHashMap<>();
        for (JsonNode raw : items) {
            String title = clean(stripTags(raw.path("title").asText(null)));
            String url = raw.path("originallink").asText("");
            if (url.isBlank()) url = raw.path("link").asText("");
            if (title == null || !(url.startsWith("https://") || url.startsWith("http://"))) continue;
            OffsetDateTime seen;
            try { seen = OffsetDateTime.parse(raw.path("pubDate").asText(""), DateTimeFormatter.RFC_1123_DATE_TIME); } catch (RuntimeException e) { continue; }
            String host = URI.create(url).getHost();
            String domain = host == null ? null : host.replaceFirst("^www\\.", "");
            String summary = clean(stripTags(raw.path("description").asText(null)));
            Article article = new Article(title, url, domain, seen, summary);
            (title.contains(companyName) ? unique : loose).putIfAbsent(title, article);
        }
        List<Article> out = new ArrayList<>(unique.values());
        if (out.size() < 5) loose.values().stream().limit(5 - out.size()).forEach(out::add);
        return out;
    }

    private static String stripTags(String value) { return value == null ? null : value.replaceAll("<[^>]+>", ""); }

    static URI uri(String companyName) {
        String query = "\"" + companyName + "\" sourcelang:korean";
        return URI.create(ENDPOINT + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20")
                + "&mode=artlist&maxrecords=20&timespan=14d&sort=datedesc&format=json");
    }

    List<Article> parse(String body) {
        if (body == null || body.isBlank() || !body.trim().startsWith("{")) return List.of();
        JsonNode root = json.readTree(body);
        JsonNode articles = root.get("articles");
        if (articles == null || !articles.isArray()) return List.of();
        Map<String, Article> unique = new LinkedHashMap<>();
        for (JsonNode raw : articles) {
            String title = clean(raw.path("title").asText(null));
            String url = raw.path("url").asText(null);
            if (title == null || url == null || !(url.startsWith("https://") || url.startsWith("http://"))) continue;
            OffsetDateTime seen;
            try { seen = OffsetDateTime.parse(raw.path("seendate").asText(""), SEEN); } catch (RuntimeException e) { continue; }
            String domain = clean(raw.path("domain").asText(null));
            if (domain == null) domain = URI.create(url).getHost();
            unique.putIfAbsent(url, new Article(title, url, domain, seen));
        }
        return new ArrayList<>(unique.values());
    }

    private static String clean(String value) {
        if (value == null) return null;
        String cleaned = HtmlUtils.htmlUnescape(value).replaceAll("\\s+", " ").trim();
        return cleaned.isEmpty() ? null : cleaned.length() > 300 ? cleaned.substring(0, 300) : cleaned;
    }

    static HeaderSender defaultHeaderSender() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        return (uri, headers) -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET();
            headers.forEach(request::header);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        };
    }

    static Sender defaultSender() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        return uri -> client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
