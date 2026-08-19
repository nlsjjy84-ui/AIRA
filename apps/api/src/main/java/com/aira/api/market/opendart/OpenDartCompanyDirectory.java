package com.aira.api.market.opendart;

import java.util.List;
import java.util.Optional;

public final class OpenDartCompanyDirectory {
    private final List<OpenDartCompanyDirectoryRecord> records;

    public OpenDartCompanyDirectory(List<OpenDartCompanyDirectoryRecord> records) {
        if (records == null || records.isEmpty() || records.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("OpenDART company directory records are required");
        }
        this.records = List.copyOf(records);
    }

    public List<OpenDartCompanyDirectoryRecord> records() {
        return records;
    }

    public Optional<OpenDartCompanyDirectoryRecord> findByCorpCode(String corpCode) {
        if (corpCode == null || !corpCode.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("OpenDART corp code must contain exactly 8 digits");
        }
        return unique(records.stream().filter(record -> corpCode.equals(record.corpCode())).toList(),
                "OpenDART corp code is duplicated");
    }

    public Optional<OpenDartCompanyDirectoryRecord> findByStockCode(String stockCode) {
        if (stockCode == null || !stockCode.matches("\\S{6}")) {
            throw new IllegalArgumentException(
                    "OpenDART stock code must contain exactly 6 non-whitespace characters");
        }
        return unique(records.stream().filter(record -> stockCode.equals(record.stockCode())).toList(),
                "OpenDART stock code is ambiguous");
    }

    private static Optional<OpenDartCompanyDirectoryRecord> unique(
            List<OpenDartCompanyDirectoryRecord> matches, String duplicateMessage) {
        if (matches.size() > 1) {
            throw new OpenDartProviderException(
                    OpenDartProviderException.Category.MALFORMED_RESPONSE, duplicateMessage);
        }
        return matches.stream().findFirst();
    }
}
