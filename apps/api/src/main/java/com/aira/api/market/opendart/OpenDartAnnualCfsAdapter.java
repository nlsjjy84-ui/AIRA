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
import java.util.Comparator;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OpenDartAnnualCfsAdapter {
    private static final String NAMESPACE = "OPENDART";
    private static final String IDENTIFIER_TYPE = "CORP_CODE";
    private static final String REPORT_CODE = "11011";
    private static final String CFS = "CFS";
    private static final SourceRegistration SOURCE = new SourceRegistration(
            SourceType.REGULATOR, "opendart", "OpenDART", "opendart.fss.or.kr");
    private static final Map<FactPredicate, List<String>> ACCOUNT_IDS = Map.of(
            FactPredicate.REVENUE, List.of("ifrs_Revenue", "ifrs-full_Revenue"),
            FactPredicate.OPERATING_INCOME, List.of("dart_OperatingIncomeLoss"));

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
        return ingestFiling(request.context(),
                List.of(request.predicate())).getFirst();
    }

    @Transactional
    public List<IngestionReceipt> ingestFiling(
            OpenDartAnnualCfsContext context, List<FactPredicate> predicates) {
        if (context == null) {
            throw new IllegalArgumentException("OpenDART request context is required");
        }
        if (predicates == null || predicates.isEmpty() || predicates.stream().anyMatch(p -> p == null)) {
            throw new IllegalArgumentException("At least one fact predicate is required");
        }
        OpenDartFinancialResponse response = client.fetch(context.corpCode(), context.businessYear());
        requireSuccess(response);
        UUID entityId = identifiers.findEntity(
                        new ExternalIdentifierKey(NAMESPACE, IDENTIFIER_TYPE, context.corpCode()))
                .filter(entity -> entity.getEntityType() == EntityType.COMPANY)
                .map(entity -> entity.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "OpenDART corp code is not mapped to a company entity"));

        List<OpenDartFinancialRow> selected = predicates.stream()
                .map(predicate -> selectRow(response, context, predicate))
                .toList();
        String receiptNumber = selected.getFirst().receiptNumber();
        if (selected.stream().anyMatch(row -> !receiptNumber.equals(row.receiptNumber()))) {
            throw malformed("supported accounts do not belong to one filing");
        }
        // v1 is CFS-only. Revisit content-hash/revision semantics before combining OFS
        // or another OpenDART endpoint with this filing Evidence identity.
        byte[] filingHash = filingContentHash(response, receiptNumber, context);
        var receipts = new java.util.ArrayList<IngestionReceipt>();
        for (int index = 0; index < predicates.size(); index++) {
            receipts.add(boundary.ingest(toInput(
                    context, predicates.get(index), entityId, selected.get(index), filingHash)));
        }
        return List.copyOf(receipts);
    }

    private static OpenDartFinancialRow selectRow(OpenDartFinancialResponse response,
            OpenDartAnnualCfsContext context, FactPredicate predicate) {
        List<String> accountIds = ACCOUNT_IDS.get(predicate);
        if (accountIds == null) {
            throw new IllegalArgumentException("Unsupported fact predicate");
        }
        List<OpenDartFinancialRow> matches = response.list().stream()
                .filter(row -> context.reportCode().equals(row.reportCode()))
                .filter(row -> Integer.toString(context.businessYear()).equals(row.businessYear()))
                .filter(row -> row.corpCode() == null || context.corpCode().equals(row.corpCode()))
                .filter(row -> row.financialStatementDivision() == null
                        || context.financialStatementDivision()
                                .equals(row.financialStatementDivision()))
                .filter(row -> accountIds.contains(row.accountId()))
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
        return matches.getFirst();
    }

    private SourceAwareEarningsIngestionInput toInput(
            OpenDartAnnualCfsContext context, FactPredicate predicate,
            UUID entityId, OpenDartFinancialRow row,
            byte[] filingHash) {
        Period period = annualPeriod(context.businessYear(), context.fiscalYearEndMonth());
        BigDecimal amount = parseAmount(row.currentTermAmount());
        String currency = requireCurrency(row.currency());
        String locator = context.financialStatementDivision() + "/"
                + required(row.statementDivision(), "statement division")
                + "/" + row.accountId() + "/thstrm_amount";
        OffsetDateTime collectedAt = OffsetDateTime.now(clock);
        var evidence = new EvidenceRegistration(
                EvidenceType.DISCLOSURE,
                required(row.receiptNumber(), "receipt number"),
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + row.receiptNumber(),
                "OpenDART annual CFS filing", filingHash,
                null, null, collectedAt, 1);
        return new SourceAwareEarningsIngestionInput(
                SOURCE, evidence, entityId, period.end(),
                "Annual CFS " + predicate,
                period.end().atStartOfDay().atOffset(ZoneOffset.UTC),
                predicate, amount, currency, period.start(), period.end(), locator);
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

    static Period annualPeriod(int businessYear, int fiscalYearEndMonth) {
        LocalDate end = LocalDate.of(businessYear, fiscalYearEndMonth, 1)
                .with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
        return new Period(end.minusYears(1).plusDays(1), end);
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

    private static byte[] filingContentHash(
            OpenDartFinancialResponse response, String receiptNumber,
            OpenDartAnnualCfsContext context) {
        Comparator<OpenDartFinancialRow> order = Comparator
                .comparing(OpenDartAnnualCfsAdapter::stableRow);
        String canonical = response.list().stream()
                .filter(row -> receiptNumber.equals(row.receiptNumber()))
                .sorted(order)
                .map(OpenDartAnnualCfsAdapter::stableRow)
                .collect(java.util.stream.Collectors.joining("\n",
                        "opendart-filing-v1\n" + stableContext(context) + "\n", ""));
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static String stableContext(OpenDartAnnualCfsContext context) {
        return String.join("/", context.corpCode(), Integer.toString(context.businessYear()),
                context.reportCode(), context.financialStatementDivision());
    }

    private static String stableRow(OpenDartFinancialRow row) {
        return java.util.Arrays.asList(row.receiptNumber(), row.businessYear(), row.reportCode(),
                        row.corpCode(), row.financialStatementDivision(), row.statementDivision(),
                        row.statementName(), row.accountId(), row.accountName(), row.accountDetail(),
                        row.currentTerm(), row.currentTermName(), row.currentTermAmount(),
                        row.previousTermName(), row.previousTermAmount(),
                        row.beforePreviousTermName(), row.beforePreviousTermAmount(), row.order(),
                        row.currency())
                .stream().map(OpenDartAnnualCfsAdapter::lengthPrefixed)
                .collect(java.util.stream.Collectors.joining());
    }

    private static String lengthPrefixed(String value) {
        return value == null ? "-1:" : value.length() + ":" + value;
    }

    private static OpenDartProviderException malformed(String detail) {
        return new OpenDartProviderException(
                OpenDartProviderException.Category.MALFORMED_RESPONSE,
                "OpenDART " + detail);
    }

    record Period(LocalDate start, LocalDate end) {
        Period {
            if (end.isBefore(start)) {
                throw malformed("current-term period is reversed");
            }
        }
    }
}
