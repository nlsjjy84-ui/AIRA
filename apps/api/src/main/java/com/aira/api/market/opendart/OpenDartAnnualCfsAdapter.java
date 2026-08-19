package com.aira.api.market.opendart;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.ingestion.EarningsIngestionBoundary;
import com.aira.api.market.ingestion.EvidenceRegistration;
import com.aira.api.market.ingestion.IngestionReceipt;
import com.aira.api.market.ingestion.SourceAwareEarningsIngestionInput;
import com.aira.api.market.service.EntityExternalIdentifierRegistryService;
import com.aira.api.market.service.ExternalIdentifierKey;
import com.aira.api.market.service.SourceRegistration;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class OpenDartAnnualCfsAdapter {
    private static final String NAMESPACE = "OPENDART";
    private static final String IDENTIFIER_TYPE = "CORP_CODE";
    private static final String REPORT_CODE = "11011";
    private static final String CFS = "CFS";
    private static final SourceRegistration SOURCE = new SourceRegistration(
            SourceType.REGULATOR, "opendart", "OpenDART", "opendart.fss.or.kr");
    private static final Map<FactPredicate, String> ACCOUNT_IDS = Map.of(
            FactPredicate.REVENUE, "ifrs_Revenue",
            FactPredicate.OPERATING_INCOME, "dart_OperatingIncomeLoss");
    private static final DateTimeFormatter DART_DATE = DateTimeFormatter.ofPattern("uuuu.MM.dd");

    private final OpenDartAnnualCfsClient client;
    private final EntityExternalIdentifierRegistryService identifiers;
    private final EarningsIngestionBoundary boundary;
    private final Clock clock;

    @Autowired
    public OpenDartAnnualCfsAdapter(OpenDartAnnualCfsClient client,
            EntityExternalIdentifierRegistryService identifiers,
            EarningsIngestionBoundary boundary) {
        this(client, identifiers, boundary, Clock.systemUTC());
    }

    OpenDartAnnualCfsAdapter(OpenDartAnnualCfsClient client,
            EntityExternalIdentifierRegistryService identifiers,
            EarningsIngestionBoundary boundary, Clock clock) {
        this.client = client;
        this.identifiers = identifiers;
        this.boundary = boundary;
        this.clock = clock;
    }

    public IngestionReceipt ingest(OpenDartAnnualCfsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("OpenDART request is required");
        }
        OpenDartFinancialResponse response = client.fetch(request.corpCode(), request.businessYear());
        requireSuccess(response);
        UUID entityId = identifiers.findEntity(
                        new ExternalIdentifierKey(NAMESPACE, IDENTIFIER_TYPE, request.corpCode()))
                .filter(entity -> entity.getEntityType() == EntityType.COMPANY)
                .map(entity -> entity.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "OpenDART corp code is not mapped to a company entity"));

        String accountId = ACCOUNT_IDS.get(request.predicate());
        List<OpenDartFinancialRow> matches = response.list().stream()
                .filter(row -> REPORT_CODE.equals(row.reportCode()))
                .filter(row -> CFS.equals(row.financialStatementDivision()))
                .filter(row -> Integer.toString(request.businessYear()).equals(row.businessYear()))
                .filter(row -> accountId.equals(row.accountId()))
                .toList();
        if (matches.isEmpty()) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.NO_DATA,
                    "Supported annual CFS account was not returned");
        }
        if (matches.size() != 1) {
            throw new OpenDartProviderException(
                    OpenDartProviderException.Category.MALFORMED_RESPONSE,
                    "OpenDART returned an ambiguous annual CFS account");
        }
        return boundary.ingest(toInput(request, entityId, matches.getFirst()));
    }

    private SourceAwareEarningsIngestionInput toInput(
            OpenDartAnnualCfsRequest request, UUID entityId, OpenDartFinancialRow row) {
        Period period = parsePeriod(row.currentTerm());
        BigDecimal amount = parseAmount(row.currentTermAmount());
        String currency = requireCurrency(row.currency());
        String locator = "CFS/" + required(row.statementDivision(), "statement division")
                + "/" + row.accountId() + "/thstrm_amount";
        OffsetDateTime collectedAt = OffsetDateTime.now(clock);
        var evidence = new EvidenceRegistration(
                EvidenceType.DISCLOSURE,
                required(row.receiptNumber(), "receipt number"),
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + row.receiptNumber(),
                "OpenDART annual CFS " + row.accountId(),
                contentHash(request.corpCode(), row, period, amount, currency),
                locator, null, collectedAt, 1);
        return new SourceAwareEarningsIngestionInput(
                SOURCE, evidence, entityId, period.end(),
                "Annual CFS " + request.predicate(),
                period.end().atStartOfDay().atOffset(ZoneOffset.UTC),
                request.predicate(), amount, currency, period.start(), period.end(), locator);
    }

    static BigDecimal parseAmount(String value) {
        if (value == null || value.isBlank()) {
            throw malformed("current-term amount is missing");
        }
        try {
            return new BigDecimal(value.trim().replace(",", ""));
        } catch (NumberFormatException exception) {
            throw malformed("current-term amount is malformed");
        }
    }

    private static Period parsePeriod(String value) {
        if (value == null) {
            throw malformed("current-term period is missing");
        }
        String[] parts = value.trim().split("\\s*~\\s*", -1);
        if (parts.length != 2) {
            throw malformed("current-term period is malformed");
        }
        try {
            return new Period(LocalDate.parse(parts[0], DART_DATE),
                    LocalDate.parse(parts[1], DART_DATE));
        } catch (DateTimeParseException exception) {
            throw malformed("current-term period is malformed");
        }
    }

    private static String requireCurrency(String value) {
        String currency = required(value, "currency");
        if (!currency.matches("[A-Z]{3}")) {
            throw malformed("currency is unsupported");
        }
        return currency;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw malformed(field + " is missing");
        }
        return value.trim();
    }

    private static void requireSuccess(OpenDartFinancialResponse response) {
        if (response == null || response.status() == null) {
            throw malformed("provider status is missing");
        }
        if ("000".equals(response.status())) {
            if (response.list() == null) {
                throw malformed("provider list is missing");
            }
            return;
        }
        OpenDartProviderException.Category category = switch (response.status()) {
            case "013" -> OpenDartProviderException.Category.NO_DATA;
            case "010", "011", "012", "901" ->
                    OpenDartProviderException.Category.AUTHENTICATION;
            case "020", "021" -> OpenDartProviderException.Category.RATE_LIMIT;
            case "100" -> OpenDartProviderException.Category.INVALID_REQUEST;
            default -> OpenDartProviderException.Category.PROVIDER_FAILURE;
        };
        throw new OpenDartProviderException(category,
                "OpenDART request failed with status " + response.status());
    }

    private static byte[] contentHash(String corpCode, OpenDartFinancialRow row,
            Period period, BigDecimal amount, String currency) {
        String canonical = String.join("\n",
                "opendart-annual-cfs-v1", corpCode, row.receiptNumber(), row.businessYear(),
                row.reportCode(), row.financialStatementDivision(), row.statementDivision(),
                row.accountId(), period.start().toString(), period.end().toString(), currency,
                amount.stripTrailingZeros().toPlainString());
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static OpenDartProviderException malformed(String detail) {
        return new OpenDartProviderException(
                OpenDartProviderException.Category.MALFORMED_RESPONSE,
                "OpenDART " + detail);
    }

    private record Period(LocalDate start, LocalDate end) {
        private Period {
            if (end.isBefore(start)) {
                throw malformed("current-term period is reversed");
            }
        }
    }
}
