package com.aira.api.assist;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** OpenDART document.xml (a zip of XML) turned into plain text. Nothing is stored. */
@Component
public class OpenDartFilingTextSource implements FilingTextSource {
    static final int MAX_UNZIPPED_BYTES = 12 * 1024 * 1024;
    static final int MAX_CHARS = 3_000_000;
    private static final Pattern STATUS = Pattern.compile("<status>\\s*(\\d+)\\s*</status>");
    private static final Pattern ENCODING = Pattern.compile("encoding=[\"']([A-Za-z0-9_\\-]+)[\"']");

    private final HttpClient http;
    private final String apiKey;
    private final String baseUrl;

    @Autowired
    public OpenDartFilingTextSource(@Value("${aira.opendart.api-key:}") String apiKey) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build(), apiKey,
                "https://opendart.fss.or.kr/api/document.xml");
    }

    OpenDartFilingTextSource(HttpClient http, String apiKey, String baseUrl) {
        this.http = http;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.baseUrl = baseUrl;
    }

    @Override
    public String fetch(String receiptNumber) {
        if (receiptNumber == null || !receiptNumber.matches("\\d{14}")) throw new IllegalStateException("FAILED");
        if (apiKey.isBlank()) throw new IllegalStateException("NO_KEY");
        try {
            URI uri = URI.create(baseUrl + "?crtfc_key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)
                    + "&rcept_no=" + receiptNumber);
            HttpResponse<byte[]> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(40)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) throw new IllegalStateException("FAILED:HTTP_" + response.statusCode());
            return parse(response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("FAILED");
        } catch (IllegalStateException failure) {
            throw failure;
        } catch (Exception failure) {
            // Only the exception class name is kept: the credential-bearing URI must not escape.
            throw new IllegalStateException("FAILED:" + failure.getClass().getSimpleName());
        }
    }

    /** Package-private so tests can feed a zip or an error XML without HTTP. */
    static String parse(byte[] body) {
        if (body == null || body.length < 4) throw new IllegalStateException("FAILED:EMPTY_BODY");
        if (body[0] != 'P' || body[1] != 'K') {
            Matcher status = STATUS.matcher(new String(body, StandardCharsets.UTF_8));
            String code = status.find() ? status.group(1) : "";
            switch (code) {
                case "013", "014" -> throw new IllegalStateException("NOT_FOUND");
                case "020" -> throw new IllegalStateException("RATE");
                case "010", "011", "012" -> throw new IllegalStateException("NO_KEY");
                default -> throw new IllegalStateException(code.isEmpty()
                        ? "FAILED:NOT_ZIP_" + snippet(body) : "FAILED:STATUS_" + code);
            }
        }
        String text;
        try {
            text = unzip(body, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException malformedName) {
            // Some archives store entry names in a legacy Korean encoding.
            text = unzip(body, Charset.forName("x-windows-949"));
        }
        if (text.isBlank()) throw new IllegalStateException("NOT_FOUND");
        return text;
    }

    private static String unzip(byte[] body, Charset names) {
        StringBuilder text = new StringBuilder();
        int remaining = MAX_UNZIPPED_BYTES;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(body), names)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) continue;
                String name = entry.getName().toLowerCase();
                boolean binary = name.endsWith(".pdf") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                        || name.endsWith(".png") || name.endsWith(".gif") || name.endsWith(".zip");
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int read;
                while ((read = zip.read(buffer)) > 0) {
                    remaining -= read;
                    if (remaining < 0) throw new IllegalStateException("TOO_BIG");
                    if (!binary) bytes.write(buffer, 0, read);
                }
                if (binary) continue;
                byte[] data = bytes.toByteArray();
                text.append(FilingText.toPlain(new String(data, charsetOf(data)))).append('\n');
                if (text.length() > MAX_CHARS) throw new IllegalStateException("TOO_BIG");
            }
        } catch (IOException failure) {
            throw new IllegalStateException("FAILED:UNZIP");
        }
        return text.toString();
    }

    private static String snippet(byte[] body) {
        String head = new String(body, 0, Math.min(body.length, 60), StandardCharsets.UTF_8);
        return head.replaceAll("[^\\p{L}\\p{N}<>/_.-]", "_");
    }

    private static Charset charsetOf(byte[] data) {
        String head = new String(data, 0, Math.min(data.length, 200), StandardCharsets.ISO_8859_1);
        Matcher matcher = ENCODING.matcher(head);
        if (matcher.find()) {
            try {
                return Charset.forName(matcher.group(1));
            } catch (RuntimeException ignored) {
                // fall through to UTF-8
            }
        }
        return StandardCharsets.UTF_8;
    }
}
