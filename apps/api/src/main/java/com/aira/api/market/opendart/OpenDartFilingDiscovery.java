package com.aira.api.market.opendart;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * list.json의 모든 page를 검증한 뒤 validated filing candidates만 반환한다.
 * 이 계층은 filing을 선택하거나 Evidence/Event/Fact를 기록하지 않는다.
 */
@Service
public final class OpenDartFilingDiscovery {
    private static final DateTimeFormatter RECEIPT_DATE = DateTimeFormatter
            .ofPattern("uuuuMMdd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final Comparator<OpenDartFilingCandidate> ORDER = Comparator
            .comparing(OpenDartFilingCandidate::receiptDate)
            .thenComparing(OpenDartFilingCandidate::receiptNumber);

    private final OpenDartFilingPageClient pages;

    public OpenDartFilingDiscovery(OpenDartFilingPageClient pages) {
        this.pages = pages;
    }

    public List<OpenDartFilingCandidate> discover(OpenDartFilingDiscoveryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("OpenDART filing discovery request is required");
        }

        Map<String, OpenDartFilingCandidate> byReceipt = new LinkedHashMap<>();
        Integer expectedTotalPages = null;
        Integer expectedTotalCount = null;
        for (int pageNumber = 1; ; pageNumber++) {
            OpenDartFilingPageResponse page = pages.fetch(request, pageNumber);
            if (isNoData(page, pageNumber)) {
                return List.of();
            }
            requireSuccess(page);
            validatePageMeta(page, pageNumber);

            if (expectedTotalPages == null) {
                expectedTotalPages = page.totalPages();
                expectedTotalCount = page.totalCount();
            } else if (!expectedTotalPages.equals(page.totalPages())
                    || !expectedTotalCount.equals(page.totalCount())) {
                throw malformed("pagination totals changed during traversal");
            }

            List<OpenDartFilingTransportRow> rows = page.list();
            if (rows == null || rows.size() > HttpOpenDartFilingPageClient.PAGE_COUNT) {
                throw malformed("filing page rows are missing or exceed page_count");
            }
            if (page.totalCount() > 0 && pageNumber < page.totalPages() && rows.isEmpty()) {
                // 중간 page가 비면 이후 receipt 누락 여부를 증명할 수 없으므로 partial success를 금지한다.
                throw malformed("filing pagination became incomplete");
            }
            for (OpenDartFilingTransportRow row : rows) {
                OpenDartFilingCandidate candidate = toCandidate(request, row);
                OpenDartFilingCandidate existing = byReceipt.putIfAbsent(candidate.receiptNumber(), candidate);
                if (existing != null && !existing.equals(candidate)) {
                    throw malformed("same receipt number has conflicting filing metadata");
                }
            }

            if (pageNumber >= page.totalPages()) {
                break;
            }
        }

        // Provider page overlap은 dedup할 수 있지만 unique receipt 수가 total_count와 다르면
        // complete traversal을 증명할 수 없으므로 성공으로 승격하지 않는다.
        if (expectedTotalCount == null || byReceipt.size() != expectedTotalCount) {
            throw malformed("validated receipt count does not match provider total_count");
        }
        ArrayList<OpenDartFilingCandidate> ordered = new ArrayList<>(byReceipt.values());
        ordered.sort(ORDER);
        return List.copyOf(ordered);
    }

    private static boolean isNoData(OpenDartFilingPageResponse page, int pageNumber) {
        if (page == null || page.status() == null) {
            throw malformed("filing page status is missing");
        }
        if (!"013".equals(page.status())) {
            return false;
        }
        if (pageNumber != 1) {
            throw malformed("provider returned no-data after pagination had started");
        }
        return true;
    }

    private static void requireSuccess(OpenDartFilingPageResponse page) {
        if ("000".equals(page.status())) {
            return;
        }
        OpenDartProviderException.Category category = switch (page.status()) {
            case "010", "011", "012" -> OpenDartProviderException.Category.AUTHENTICATION;
            case "020" -> OpenDartProviderException.Category.RATE_LIMIT;
            case "100", "101" -> OpenDartProviderException.Category.INVALID_REQUEST;
            default -> OpenDartProviderException.Category.PROVIDER_FAILURE;
        };
        throw new OpenDartProviderException(category,
                "OpenDART filing discovery returned provider status " + page.status());
    }

    private static void validatePageMeta(OpenDartFilingPageResponse page, int requestedPage) {
        if (page.pageNumber() == null || page.pageNumber() != requestedPage) {
            throw malformed("filing page number does not match request");
        }
        if (page.pageCount() == null || page.pageCount() != HttpOpenDartFilingPageClient.PAGE_COUNT) {
            throw malformed("filing page_count does not match request");
        }
        if (page.totalCount() == null || page.totalCount() < 0
                || page.totalPages() == null || page.totalPages() < 0) {
            throw malformed("filing pagination totals are invalid");
        }
        if (page.totalCount() == 0) {
            if (page.totalPages() > 1 || page.list() == null || !page.list().isEmpty()) {
                throw malformed("empty filing result has inconsistent pagination metadata");
            }
            return;
        }
        if (page.totalPages() < 1 || requestedPage > page.totalPages()) {
            throw malformed("filing pagination bounds are invalid");
        }
        long maxRepresentable = (long) page.totalPages() * HttpOpenDartFilingPageClient.PAGE_COUNT;
        if (page.totalCount() > maxRepresentable) {
            throw malformed("filing total_count exceeds pagination capacity");
        }
    }

    private static OpenDartFilingCandidate toCandidate(
            OpenDartFilingDiscoveryRequest request, OpenDartFilingTransportRow row) {
        if (row == null) {
            throw malformed("filing row is missing");
        }
        String corpCode = requireDigits(row.corpCode(), 8, "corp_code");
        if (!request.corpCode().equals(corpCode)) {
            throw malformed("filing row corp_code does not match request");
        }
        String receiptNumber = requireDigits(row.receiptNumber(), 14, "rcept_no");
        LocalDate receiptDate = parseReceiptDate(row.receiptDate());
        return new OpenDartFilingCandidate(
                optional(row.corpClass()),
                corpCode,
                required(row.corpName(), "corp_name"),
                optional(row.stockCode()),
                required(row.reportName(), "report_nm"),
                receiptNumber,
                required(row.filerName(), "flr_nm"),
                receiptDate,
                optional(row.rm()));
    }

    private static LocalDate parseReceiptDate(String value) {
        String normalized = required(value, "rcept_dt");
        if (!normalized.matches("[0-9]{8}")) {
            throw malformed("rcept_dt must be YYYYMMDD");
        }
        try {
            return LocalDate.parse(normalized, RECEIPT_DATE);
        } catch (DateTimeParseException exception) {
            throw malformed("rcept_dt is not a valid calendar date");
        }
    }

    private static String requireDigits(String value, int length, String field) {
        String normalized = required(value, field);
        if (!normalized.matches("[0-9]{" + length + "}")) {
            throw malformed(field + " has invalid length or characters");
        }
        return normalized;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw malformed(field + " is missing");
        }
        return value.trim();
    }

    private static String optional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static OpenDartProviderException malformed(String detail) {
        return new OpenDartProviderException(OpenDartProviderException.Category.MALFORMED_RESPONSE,
                "OpenDART filing discovery " + detail);
    }
}
