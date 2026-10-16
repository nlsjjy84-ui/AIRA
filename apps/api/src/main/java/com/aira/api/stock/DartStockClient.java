package com.aira.api.stock;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 종목 화면용 OpenDART 호출: 회사 개황(company.json), 공시 목록(list.json), 다중회사 주요계정(fnlttMultiAcnt.json). */
@Component
public class DartStockClient {
    static final String BASE = "https://opendart.fss.or.kr/api/";
    private static final DateTimeFormatter DAY = DateTimeFormatter.BASIC_ISO_DATE;

    public interface Sender { String get(URI uri) throws IOException, InterruptedException; }

    private final Sender sender;
    private final ObjectMapper json;
    private final String apiKey;

    @Autowired
    public DartStockClient(ObjectMapper json, @Value("${aira.opendart.api-key:}") String apiKey) {
        this(defaultSender(), json, apiKey);
    }

    DartStockClient(Sender sender, ObjectMapper json, String apiKey) {
        this.sender = sender;
        this.json = json;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    public boolean configured() { return !apiKey.isBlank(); }

    public Map<String, String> company(String corpCode) {
        JsonNode body = call("company.json", "corp_code=" + enc(corpCode));
        if (body == null) return Map.of();
        Map<String, String> out = new LinkedHashMap<>();
        for (Iterator<Map.Entry<String, JsonNode>> it = body.properties().iterator(); it.hasNext(); ) {
            var entry = it.next();
            if (entry.getValue().isValueNode()) out.put(entry.getKey(), entry.getValue().asText());
        }
        return out;
    }

    public List<Map<String, String>> filings(String corpCode, LocalDate from, LocalDate to) {
        List<Map<String, String>> all = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            JsonNode body = call("list.json", "corp_code=" + enc(corpCode) + "&bgn_de=" + from.format(DAY)
                    + "&end_de=" + to.format(DAY) + "&page_no=" + page + "&page_count=100");
            if (body == null) break;
            all.addAll(rows(body));
            int totalPage = body.has("total_page") ? body.get("total_page").asInt(1) : 1;
            if (page >= totalPage) break;
        }
        return all;
    }

    /** 공시 유형 하나만(A 정기공시, B 주요사항보고, I 거래소공시 등) 최근 100건. 지분 보고가 많은 회사도 정기·실적 공시를 놓치지 않게 한다. */
    public List<Map<String, String>> filingsOfType(String corpCode, LocalDate from, LocalDate to, String type) {
        if (!type.matches("[A-J]")) throw new IllegalArgumentException("Unsupported filing type");
        JsonNode body = call("list.json", "corp_code=" + enc(corpCode) + "&bgn_de=" + from.format(DAY)
                + "&end_de=" + to.format(DAY) + "&pblntf_ty=" + type + "&page_no=1&page_count=100");
        return body == null ? List.of() : rows(body);
    }

    /** 전체 시장 최근 공시(유가·코스닥만). 회사 지정 없이 받을 수 있는 기간은 3개월 이내다. */
    public List<Map<String, String>> marketFilings(LocalDate from, LocalDate to, String corpClass) {
        JsonNode body = call("list.json", "bgn_de=" + from.format(DAY) + "&end_de=" + to.format(DAY)
                + "&corp_cls=" + enc(corpClass) + "&page_no=1&page_count=100");
        return body == null ? List.of() : rows(body);
    }

    static final String REPORT_SECTIONS = "alotMatter|hyslrSttus|hyslrChgSttus|empSttus|exctvSttus|hmvAuditIndvdlBySttus|hmvAuditAllSttus|indvdlByPay"
            + "|tesstkAcqsDspsSttus|irdsSttus|mrhlSttus|stockTotqySttus|otrCprInvstmntSttus|cprndNrdmpBlce|accnutAdtorNmNdAdtOpinion";

    /** 정기보고서 주요정보(배당 alotMatter, 최대주주 hyslrSttus, 직원 empSttus 등). 사업보고서(11011) 기준. */
    public List<Map<String, String>> reportSection(String section, String corpCode, int businessYear) {
        if (!section.matches(REPORT_SECTIONS)) {
            throw new IllegalArgumentException("Unsupported report section");
        }
        JsonNode body = call(section + ".json", "corp_code=" + enc(corpCode) + "&bsns_year=" + businessYear + "&reprt_code=11011");
        return body == null ? List.of() : rows(body);
    }

    public List<Map<String, String>> multiAccounts(List<String> corpCodes, int businessYear) {
        return multiAccounts(corpCodes, businessYear, "11011");
    }

    /** reportCode: 11011 사업보고서, 11013 1분기, 11012 반기, 11014 3분기. */
    public List<Map<String, String>> multiAccounts(List<String> corpCodes, int businessYear, String reportCode) {
        if (!reportCode.matches("1101[1-4]")) throw new IllegalArgumentException("Unsupported report code");
        JsonNode body = call("fnlttMultiAcnt.json", "corp_code=" + enc(String.join(",", corpCodes))
                + "&bsns_year=" + businessYear + "&reprt_code=" + reportCode);
        return body == null ? List.of() : rows(body);
    }

    /** 단일회사 전체 재무제표(현금흐름표 포함). fsDiv: CFS 연결 / OFS 별도. */
    public List<Map<String, String>> fullStatements(String corpCode, int businessYear, String fsDiv) {
        if (!fsDiv.matches("CFS|OFS")) throw new IllegalArgumentException("Unsupported fs_div");
        JsonNode body = call("fnlttSinglAcntAll.json", "corp_code=" + enc(corpCode) + "&bsns_year=" + businessYear
                + "&reprt_code=11011&fs_div=" + fsDiv);
        return body == null ? List.of() : rows(body);
    }

    /** XBRL 재무제표 원본 파일(zip). 사업보고서(11011) 기준. 파일이 없거나 zip이 아니면 null. */
    public byte[] xbrlZip(String receiptNumber) {
        if (receiptNumber == null || !receiptNumber.matches("[0-9]{14}")) return null;
        return zip("fnlttXbrl.xml", "&rcept_no=" + receiptNumber + "&reprt_code=11011");
    }

    /** 공시서류 원본 파일(zip). 사업보고서·공정공시 등 접수번호 하나의 본문 문서. 없거나 zip이 아니면 null. */
    public byte[] documentZip(String receiptNumber) {
        if (receiptNumber == null || !receiptNumber.matches("[0-9]{14}")) return null;
        return zip("document.xml", "&rcept_no=" + receiptNumber);
    }

    private byte[] zip(String endpoint, String query) {
        if (apiKey.isBlank()) throw new DartUnavailableException("OpenDART API key is not configured");
        URI uri = URI.create(BASE + endpoint + "?crtfc_key=" + enc(apiKey) + query);
        try {
            HttpResponse<byte[]> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                    .send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            byte[] body = response.body();
            if (response.statusCode() != 200 || body == null || body.length < 4 || body[0] != 'P' || body[1] != 'K') return null;
            return body;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DartUnavailableException("OpenDART request interrupted");
        } catch (IOException e) {
            throw new DartUnavailableException("OpenDART request failed");
        }
    }

    /** 지분공시: elestock(임원·주요주주 소유보고), majorstock(대량보유 상황보고). */
    public List<Map<String, String>> ownershipReports(String section, String corpCode) {
        if (!section.matches("elestock|majorstock")) throw new IllegalArgumentException("Unsupported ownership section");
        JsonNode body = call(section + ".json", "corp_code=" + enc(corpCode));
        return body == null ? List.of() : rows(body);
    }

    /** 성공이면 본문, "조회된 데이터가 없음(013)"이면 null. 그 밖의 상태는 예외로 알린다. */
    private JsonNode call(String endpoint, String query) {
        if (apiKey.isBlank()) throw new DartUnavailableException("OpenDART API key is not configured");
        URI uri = URI.create(BASE + endpoint + "?crtfc_key=" + enc(apiKey) + "&" + query);
        String text;
        try {
            text = sender.get(uri);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DartUnavailableException("OpenDART request interrupted");
        } catch (IOException e) {
            throw new DartUnavailableException("OpenDART request failed");
        }
        JsonNode body;
        try {
            body = json.readTree(text);
        } catch (RuntimeException e) {
            throw new DartUnavailableException("OpenDART response could not be read");
        }
        String status = body.has("status") ? body.get("status").asText() : "";
        if ("013".equals(status)) return null;
        if (!"000".equals(status)) throw new DartUnavailableException("OpenDART status " + status);
        return body;
    }

    private static List<Map<String, String>> rows(JsonNode body) {
        List<Map<String, String>> out = new ArrayList<>();
        JsonNode list = body.get("list");
        if (list == null || !list.isArray()) return out;
        for (JsonNode row : list) {
            Map<String, String> map = new LinkedHashMap<>();
            for (Iterator<Map.Entry<String, JsonNode>> it = row.properties().iterator(); it.hasNext(); ) {
                var entry = it.next();
                map.put(entry.getKey(), entry.getValue().isNull() ? null : entry.getValue().asText());
            }
            out.add(map);
        }
        return out;
    }

    private static String enc(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }

    static Sender defaultSender() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        return uri -> {
            HttpResponse<String> response = client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build(),
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
            return response.body();
        };
    }

    public static class DartUnavailableException extends RuntimeException {
        public DartUnavailableException(String message) { super(message); }
    }
}
