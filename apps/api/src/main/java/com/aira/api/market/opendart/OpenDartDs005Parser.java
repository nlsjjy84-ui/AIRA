package com.aira.api.market.opendart;

import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.ingestion.EvidenceRegistration;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class OpenDartDs005Parser {
    public record Result(List<ValidatedMaterialEvent> receipts, List<String> rejectedReceipts) {}
    private final ObjectMapper mapper;
    public OpenDartDs005Parser(ObjectMapper mapper) { this.mapper = mapper; }

    public Result parse(OpenDartDs005Request request, String body, OffsetDateTime collectedAt) {
        if (request == null || collectedAt == null) throw new IllegalArgumentException("DS005 request and observation time are required");
        Object decoded;
        try { decoded = mapper.readValue(body, Object.class); }
        catch (RuntimeException exception) { throw malformed(); }
        if (!(decoded instanceof Map<?, ?> envelope) || !(envelope.get("status") instanceof String status)
                || !(envelope.get("message") instanceof String)) throw malformed();
        if ("013".equals(status)) return new Result(List.of(), List.of());
        if (!"000".equals(status)) throw new OpenDartProviderException(category(status), "OpenDART DS005 status " + status);
        if (!(envelope.get("list") instanceof List<?> rows)) throw malformed();
        Map<String, List<Map<String, String>>> grouped = new HashMap<>();
        List<String> rejected = new ArrayList<>();
        for (Object value : rows) {
            if (!(value instanceof Map<?, ?> raw)) { rejected.add("row without receipt: malformed structure"); continue; }
            Object rawReceipt = raw.get("rcept_no");
            String receipt = rawReceipt instanceof String s && s.matches("[0-9]{14}") ? s : null;
            if (receipt == null) { rejected.add("row without receipt: invalid identity"); continue; }
            if (!request.corpCode().equals(raw.get("corp_code"))) {
                rejected.add(receipt + ": company identity mismatch");
                grouped.remove(receipt);
                continue;
            }
            Map<String, String> normalized = new TreeMap<>();
            boolean malformed = false;
            for (var field : raw.entrySet()) {
                if (!(field.getKey() instanceof String key) || key.isBlank()
                        || !(field.getValue() == null || field.getValue() instanceof String)
                        || "crtfc_key".equals(key)) { malformed = true; break; }
                normalized.put(key, (String) field.getValue());
            }
            if (malformed) { rejected.add(receipt + ": malformed field"); grouped.remove(receipt); continue; }
            grouped.computeIfAbsent(receipt, ignored -> new ArrayList<>()).add(normalized);
        }
        List<ValidatedMaterialEvent> accepted = new ArrayList<>();
        var endpoint = OpenDartDs005Catalog.approved(request.endpointKey());
        for (var group : grouped.entrySet()) {
            String receipt = group.getKey();
            if (rejected.stream().anyMatch(s -> s.startsWith(receipt + ":"))) continue;
            List<byte[]> rowBytes = group.getValue().stream().map(OpenDartDs005Parser::canonicalRow)
                    .sorted(OpenDartDs005Parser::compareBytes).toList();
            // Repeated identical transport rows collapse; distinct rows remain in whole-receipt Evidence.
            List<byte[]> unique = new ArrayList<>();
            for (byte[] bytes : rowBytes) if (unique.isEmpty() || compareBytes(unique.getLast(), bytes) != 0) unique.add(bytes);
            byte[] hash = receiptHash(unique);
            String baseUrl = "https://opendart.fss.or.kr/api/" + endpoint.key() + ".json";
            EvidenceRegistration evidence = new EvidenceRegistration(EvidenceType.OFFICIAL_DATA,
                    "OPENDART_MATERIAL:" + endpoint.key() + ":" + receipt, baseUrl, endpoint.title(), hash,
                    endpoint.key() + "/rcept_no=" + receipt, null, collectedAt, 1);
            accepted.add(new ValidatedMaterialEvent(endpoint.key(), request.corpCode(), receipt,
                    endpoint.type(), endpoint.title(), evidence, collectedAt, request.origin()));
        }
        accepted.sort(Comparator.comparing(ValidatedMaterialEvent::receiptNumber));
        return new Result(List.copyOf(accepted), List.copyOf(rejected));
    }

    private static byte[] canonicalRow(Map<String, String> row) {
        var out = new java.io.ByteArrayOutputStream();
        for (var field : row.entrySet()) {
            put(out, field.getKey().getBytes(StandardCharsets.UTF_8));
            if (field.getValue() == null) out.writeBytes(ByteBuffer.allocate(4).putInt(-1).array());
            else put(out, field.getValue().getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
    private static void put(java.io.ByteArrayOutputStream out, byte[] value) {
        out.writeBytes(ByteBuffer.allocate(4).putInt(value.length).array());
        out.writeBytes(value);
    }
    private static byte[] receiptHash(List<byte[]> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] row : rows) {
                digest.update(ByteBuffer.allocate(4).putInt(row.length).array());
                digest.update(row);
            }
            return digest.digest();
        } catch (Exception impossible) { throw new IllegalStateException("SHA-256 unavailable", impossible); }
    }
    private static int compareBytes(byte[] a, byte[] b) { return java.util.Arrays.compareUnsigned(a, b); }
    private static OpenDartProviderException malformed() { return new OpenDartProviderException(
            OpenDartProviderException.Category.MALFORMED_RESPONSE, "OpenDART DS005 envelope is malformed"); }
    private static OpenDartProviderException.Category category(String status) {
        return switch (status) {
            case "010", "011", "012", "901" -> OpenDartProviderException.Category.AUTHENTICATION;
            case "020" -> OpenDartProviderException.Category.RATE_LIMIT;
            case "021", "100", "101" -> OpenDartProviderException.Category.INVALID_REQUEST;
            default -> OpenDartProviderException.Category.PROVIDER_FAILURE;
        };
    }
}
