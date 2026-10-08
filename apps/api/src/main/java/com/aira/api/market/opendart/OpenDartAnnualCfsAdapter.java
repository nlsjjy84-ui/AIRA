package com.aira.api.market.opendart;

import com.aira.api.market.domain.EvidenceType;
import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.ingestion.EvidenceRegistration;
import com.aira.api.market.ingestion.IngestionReceipt;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Component
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class OpenDartAnnualCfsAdapter {
    private static final Logger log = LoggerFactory.getLogger(OpenDartAnnualCfsAdapter.class);
    private static final Map<FactPredicate, List<String>> ACCOUNT_IDS = Map.of(
            FactPredicate.REVENUE, List.of("ifrs_Revenue", "ifrs-full_Revenue"),
            FactPredicate.OPERATING_INCOME, List.of("dart_OperatingIncomeLoss"),
            FactPredicate.NET_INCOME, List.of("ifrs-full_ProfitLoss", "ifrs_ProfitLoss"),
            FactPredicate.TOTAL_ASSETS, List.of("ifrs-full_Assets", "ifrs_Assets"),
            FactPredicate.TOTAL_LIABILITIES, List.of("ifrs-full_Liabilities", "ifrs_Liabilities"),
            FactPredicate.TOTAL_EQUITY, List.of("ifrs-full_Equity", "ifrs_Equity"));
    /**
     * 새로 추가한 항목은 회사마다 계정 코드가 조금씩 달라 공시에서 못 찾을 수 있다. 그 경우 이 항목만 건너뛰고
     * 매출·영업이익 수집은 그대로 진행한다. 같은 계정 코드가 재무제표 여러 곳에 나오므로 어느 표(sj_div)의
     * 값인지도 함께 확인한다. 순이익은 손익계산서(IS) 또는 포괄손익계산서(CIS), 나머지는 재무상태표(BS)다.
     */
    private static final Map<FactPredicate, java.util.Set<String>> STATEMENT_DIVISIONS = Map.of(
            FactPredicate.NET_INCOME, java.util.Set.of("IS", "CIS"),
            FactPredicate.TOTAL_ASSETS, java.util.Set.of("BS"),
            FactPredicate.TOTAL_LIABILITIES, java.util.Set.of("BS"),
            FactPredicate.TOTAL_EQUITY, java.util.Set.of("BS"));

    private final OpenDartAnnualCfsClient client;
    private final OpenDartPeriodWitnessClient witnesses;
    private final OpenDartFilingPersistence persistence;
    private final Clock clock;

    @Autowired
    public OpenDartAnnualCfsAdapter(OpenDartAnnualCfsClient client,
            OpenDartPeriodWitnessClient witnesses, OpenDartFilingPersistence persistence) {
        this(client, witnesses, persistence, Clock.systemUTC());
    }

    OpenDartAnnualCfsAdapter(OpenDartAnnualCfsClient client,
            OpenDartPeriodWitnessClient witnesses, OpenDartFilingPersistence persistence, Clock clock) {
        this.client = client;
        this.witnesses = witnesses;
        this.persistence = persistence;
        this.clock = clock;
    }

    public IngestionReceipt ingest(OpenDartAnnualCfsRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("OpenDART request is required");
        }
        return ingestFiling(request.context(), request.expectedReceiptNumber(),
                List.of(request.predicate())).getFirst();
    }

    public List<IngestionReceipt> ingestFiling(
            OpenDartAnnualCfsContext context, String expectedReceiptNumber,
            List<FactPredicate> predicates) {
        return persistence.persist(prepare(context, expectedReceiptNumber, predicates), null).receipts();
    }

    /** Keep Discovery's receipt as the validation target so another filing cannot supply the value. */
    public OpenDartPreparedFiling prepare(OpenDartAnnualCfsContext context,
            String expectedReceiptNumber, List<FactPredicate> predicates) {
        OpenDartPeriodWitnessResolver.requireReceipt(expectedReceiptNumber);
        return prepareInternal(context, expectedReceiptNumber, predicates);
    }

    /** Legacy demo bootstrap path without a Discovery receipt; canonical ingestion uses the overload above. */
    @Deprecated(forRemoval = true)
    public OpenDartPreparedFiling prepare(OpenDartAnnualCfsContext context, List<FactPredicate> predicates) {
        return prepareInternal(context, null, predicates);
    }

    private OpenDartPreparedFiling prepareInternal(OpenDartAnnualCfsContext context,
            String expectedReceiptNumber, List<FactPredicate> predicates) {
        if (context == null) {
            throw new IllegalArgumentException("OpenDART request context is required");
        }
        if (predicates == null || predicates.isEmpty() || predicates.stream().anyMatch(p -> p == null)) {
            throw new IllegalArgumentException("At least one fact predicate is required");
        }
        OpenDartFinancialResponse response = client.fetch(context.corpCode(), context.businessYear());
        requireSuccess(response);
        if (expectedReceiptNumber != null) {
            validateExpectedFiling(response, context, expectedReceiptNumber);
        }
        var chosenPredicates = new java.util.ArrayList<FactPredicate>();
        var selected = new java.util.ArrayList<OpenDartFinancialRow>();
        for (FactPredicate predicate : predicates) {
            if (STATEMENT_DIVISIONS.containsKey(predicate)) {
                var row = selectOptionalRow(response, context, expectedReceiptNumber, predicate);
                if (row == null) {
                    log.warn("OpenDART {} was not found for corp {} year {}; skipping this item",
                            predicate, context.corpCode(), context.businessYear());
                    continue;
                }
                chosenPredicates.add(predicate);
                selected.add(row);
            } else {
                chosenPredicates.add(predicate);
                selected.add(selectRow(response, context, expectedReceiptNumber, predicate));
            }
        }
        if (selected.isEmpty()) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.NO_DATA,
                    "Supported annual CFS account was not returned");
        }
        // Value rows and the period witness must describe one filing, even when the API returns mixed receipts.
        String receiptNumber = expectedReceiptNumber != null
                ? expectedReceiptNumber : selected.getFirst().receiptNumber();
        OpenDartPeriodWitnessResolver.requireReceipt(receiptNumber);
        if (selected.stream().anyMatch(row -> !receiptNumber.equals(row.receiptNumber()))) {
            throw malformed("supported accounts do not belong to one filing");
        }
        var period = OpenDartPeriodWitnessResolver.resolve(context, receiptNumber,
                witnesses.fetch(context.corpCode(), context.businessYear()), OffsetDateTime.now(clock));
        byte[] filingHash = filingContentHash(response, receiptNumber, context);
        var metrics = new java.util.ArrayList<OpenDartPreparedFiling.Metric>();
        for (int index = 0; index < chosenPredicates.size(); index++) {
            var row = selected.get(index);
            if (!context.corpCode().equals(row.corpCode())) {
                throw OpenDartPeriodWitnessResolver.blocked(
                        OpenDartProviderException.Category.PERIOD_WITNESS_IDENTITY_MISMATCH);
            }
            metrics.add(new OpenDartPreparedFiling.Metric(chosenPredicates.get(index), parseAmount(row.currentTermAmount()),
                    requireCurrency(row.currency()), context.financialStatementDivision() + "/"
                    + required(row.statementDivision(), "statement division") + "/" + row.accountId() + "/thstrm_amount"));
        }
        var valueEvidence = new EvidenceRegistration(EvidenceType.DISCLOSURE, receiptNumber,
                "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=" + receiptNumber,
                "OpenDART annual CFS filing", filingHash, null, null, OffsetDateTime.now(clock), 1);
        var prepared = new OpenDartPreparedFiling(context, valueEvidence, period, metrics);
        persistence.preflight(prepared);
        return prepared;
    }

    private static OpenDartFinancialRow selectRow(OpenDartFinancialResponse response,
            OpenDartAnnualCfsContext context, String expectedReceiptNumber,
            FactPredicate predicate) {
        List<String> accountIds = ACCOUNT_IDS.get(predicate);
        if (accountIds == null) {
            throw new IllegalArgumentException("Unsupported fact predicate");
        }
        List<OpenDartFinancialRow> matches = response.list().stream()
                .filter(row -> row != null)
                .filter(row -> expectedReceiptNumber == null
                        || expectedReceiptNumber.equals(row.receiptNumber()))
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

    /** 선택 항목: 못 찾거나 값이 서로 다른 후보가 여럿이면 null(건너뜀). 같은 값이 여러 표에 나오면 하나로 본다. */
    private static OpenDartFinancialRow selectOptionalRow(OpenDartFinancialResponse response,
            OpenDartAnnualCfsContext context, String expectedReceiptNumber, FactPredicate predicate) {
        var accountIds = ACCOUNT_IDS.get(predicate);
        var divisions = STATEMENT_DIVISIONS.get(predicate);
        List<OpenDartFinancialRow> matches = response.list().stream()
                .filter(row -> row != null)
                .filter(row -> expectedReceiptNumber == null
                        || expectedReceiptNumber.equals(row.receiptNumber()))
                .filter(row -> context.reportCode().equals(row.reportCode()))
                .filter(row -> Integer.toString(context.businessYear()).equals(row.businessYear()))
                .filter(row -> row.corpCode() == null || context.corpCode().equals(row.corpCode()))
                .filter(row -> row.financialStatementDivision() == null
                        || context.financialStatementDivision().equals(row.financialStatementDivision()))
                .filter(row -> accountIds.contains(row.accountId()))
                .filter(row -> row.statementDivision() != null && divisions.contains(row.statementDivision()))
                .filter(row -> row.currentTermAmount() != null && !row.currentTermAmount().isBlank())
                .toList();
        if (matches.isEmpty()) {
            return null;
        }
        long distinctAmounts = matches.stream()
                .map(row -> row.currentTermAmount().trim().replace(",", ""))
                .distinct().count();
        return distinctAmounts == 1 ? matches.getFirst() : null;
    }

    private static void validateExpectedFiling(OpenDartFinancialResponse response,
            OpenDartAnnualCfsContext context, String expectedReceiptNumber) {
        boolean foundExpectedReceipt = false;
        for (OpenDartFinancialRow row : response.list()) {
            if (row == null || row.receiptNumber() == null
                    || !row.receiptNumber().matches("[0-9]{14}")) {
                throw malformed("annual CFS row has invalid receipt identity");
            }
            if (!expectedReceiptNumber.equals(row.receiptNumber())) {
                continue;
            }
            foundExpectedReceipt = true;
            if (!Integer.toString(context.businessYear()).equals(row.businessYear())
                    || !context.reportCode().equals(row.reportCode())
                    || !context.corpCode().equals(row.corpCode())
                    || (row.financialStatementDivision() != null
                        && !row.financialStatementDivision().isBlank()
                        && !context.financialStatementDivision().equals(row.financialStatementDivision()))) {
                throw malformed("expected receipt has incompatible filing identity");
            }
        }
        if (!foundExpectedReceipt) {
            throw new OpenDartProviderException(OpenDartProviderException.Category.NO_DATA,
                    "Expected annual CFS receipt was not returned");
        }
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

}
