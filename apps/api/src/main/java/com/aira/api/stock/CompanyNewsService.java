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
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 회사 이름으로 최근 기사 링크를 찾는다(GDELT DOC 2.0). GDELT는 5초에 한 번만 받으므로 서버 전체에서 6초 간격을 지키고,
 * 회사별 결과를 3시간 저장해 같은 회사는 다시 묻지 않는다. 기사 본문은 받지 않고 제목·링크·매체·시각만 쓴다.
 */
@Service
public class CompanyNewsService {
    static final String ENDPOINT = "https://api.gdeltproject.org/api/v2/doc/doc";
    private static final Duration KEEP = Duration.ofHours(3);
    private static final Duration GAP = Duration.ofSeconds(6);
    private static final DateTimeFormatter SEEN = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX");

    public interface Sender { HttpResponse<String> send(URI uri) throws IOException, InterruptedException; }

    public record Article(String title, String url, String domain, OffsetDateTime seenAt) {}
    public record Result(String state, List<Article> items, Instant fetchedAt) {}

    private final Sender sender;
    private final ObjectMapper json;
    private final Clock clock;
    private final Map<String, Result> cache = new ConcurrentHashMap<>();
    private Instant lastCall = Instant.EPOCH;

    @Autowired
    public CompanyNewsService(ObjectMapper json) { this(defaultSender(), json, Clock.systemUTC()); }

    CompanyNewsService(Sender sender, ObjectMapper json, Clock clock) {
        this.sender = sender; this.json = json; this.clock = clock;
    }

    public Result latest(String companyName) {
        Instant now = clock.instant();
        Result cached = cache.get(companyName);
        if (cached != null && cached.fetchedAt().plus(KEEP).isAfter(now)) return cached;
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

    static Sender defaultSender() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();
        return uri -> client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15)).GET().build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }
}
