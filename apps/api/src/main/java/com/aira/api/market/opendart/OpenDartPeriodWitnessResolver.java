package com.aira.api.market.opendart;

import static com.aira.api.market.opendart.OpenDartProviderException.Category.*;

import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.ingestion.EvidenceRegistration;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Pattern;

public final class OpenDartPeriodWitnessResolver {
    private static final String DATE = "([0-9]{4}\\.[0-9]{2}\\.[0-9]{2})";
    private static final Pattern RANGE = Pattern.compile(DATE + "\\s*~\\s*" + DATE);
    private static final Pattern POINT = Pattern.compile(DATE + "\\s*현재");
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("uuuu.MM.dd")
            .withResolverStyle(ResolverStyle.STRICT);

    private OpenDartPeriodWitnessResolver() {}

    public static Resolved resolve(OpenDartAnnualCfsContext context, String receipt,
            OpenDartPeriodWitnessResponse response, OffsetDateTime collectedAt) {
        requireReceipt(receipt);
        if (response == null || "013".equals(response.status())) throw blocked(PERIOD_WITNESS_MISSING);
        if (!"000".equals(response.status())) {
            throw blocked(switch (response.status() == null ? "" : response.status()) {
                case "010", "011", "012", "901" -> AUTHENTICATION;
                case "020", "021" -> RATE_LIMIT;
                default -> PROVIDER_FAILURE;
            });
        }
        if (response.list() == null || response.list().isEmpty()) throw blocked(PERIOD_WITNESS_MISSING);
        var periods = new HashSet<Period>();
        var canonicalRows = new ArrayList<String>();
        for (var row : response.list()) {
            if (row == null) throw blocked(PERIOD_WITNESS_MALFORMED);
            // OFS rows are not witnesses. No value/name inference is used to select CFS rows.
            if ("OFS".equals(row.financialStatementDivision())) continue;
            if (!"CFS".equals(row.financialStatementDivision())
                    || !context.corpCode().equals(row.corpCode())
                    || !Integer.toString(context.businessYear()).equals(row.businessYear())
                    || !context.reportCode().equals(row.reportCode())
                    || !receipt.equals(row.receiptNumber())) throw blocked(PERIOD_WITNESS_IDENTITY_MISMATCH);
            String term = row.currentTerm() == null ? "" : row.currentTerm().strip();
            String normalized = "";
            if (!term.isEmpty()) {
                var duration = RANGE.matcher(term);
                var point = POINT.matcher(term);
                if (duration.matches()) {
                    LocalDate start = date(duration.group(1));
                    LocalDate end = date(duration.group(2));
                    if (start.isAfter(end)) throw blocked(PERIOD_WITNESS_MALFORMED);
                    periods.add(new Period(start, end));
                    normalized = start + "/" + end;
                } else if (point.matches()) {
                    normalized = date(point.group(1)) + "/현재";
                } else {
                    throw blocked(PERIOD_WITNESS_MALFORMED);
                }
            }
            canonicalRows.add(String.join("|", row.corpCode(), row.businessYear(), row.reportCode(),
                    row.receiptNumber(), row.financialStatementDivision(), normalized));
        }
        if (periods.isEmpty()) throw blocked(PERIOD_WITNESS_MISSING);
        if (periods.size() != 1) throw blocked(PERIOD_WITNESS_CONFLICT);
        var period = periods.iterator().next();
        canonicalRows.sort(String::compareTo);
        byte[] hash;
        try {
            hash = MessageDigest.getInstance("SHA-256").digest(
                    ("fnlttSinglAcnt-period-v1\n" + String.join("\n", canonicalRows)).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        String locator = "fnlttSinglAcnt/CFS/11011/" + receipt + "/thstrm_dt/"
                + period.start() + "/" + period.end();
        var evidence = new EvidenceRegistration(EvidenceType.OFFICIAL_DATA,
                "fnlttSinglAcnt:CFS:11011:" + receipt,
                "https://opendart.fss.or.kr/api/fnlttSinglAcnt.json?corp_code=" + context.corpCode()
                        + "&bsns_year=" + context.businessYear() + "&reprt_code=11011",
                "OpenDART annual CFS period witness", hash, locator, null, collectedAt, 1);
        return new Resolved(period.start(), period.end(), evidence, locator);
    }

    static void requireReceipt(String receipt) {
        if (receipt == null || !receipt.matches("[0-9]{14}")) throw blocked(PERIOD_WITNESS_IDENTITY_MISMATCH);
    }

    private static LocalDate date(String value) {
        try { return LocalDate.parse(value, FORMAT); }
        catch (java.time.DateTimeException invalid) { throw blocked(PERIOD_WITNESS_MALFORMED); }
    }

    static OpenDartProviderException blocked(OpenDartProviderException.Category reason) {
        return new OpenDartProviderException(reason, "OpenDART ingestion blocked: " + reason.name());
    }

    private record Period(LocalDate start, LocalDate end) {}
    public record Resolved(LocalDate start, LocalDate end, EvidenceRegistration evidence, String locator) {}
}
