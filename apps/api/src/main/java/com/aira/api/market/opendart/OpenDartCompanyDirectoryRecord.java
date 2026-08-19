package com.aira.api.market.opendart;

import java.time.LocalDate;

public record OpenDartCompanyDirectoryRecord(
        String corpCode,
        String corpName,
        String corpEnglishName,
        String stockCode,
        LocalDate modifiedDate) {

    public OpenDartCompanyDirectoryRecord {
        if (corpCode == null || !corpCode.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("OpenDART corp code must contain exactly 8 digits");
        }
        corpName = required(corpName, "OpenDART company name is required");
        corpEnglishName = optional(corpEnglishName);
        stockCode = optional(stockCode);
        if (stockCode != null && !stockCode.matches("\\S{6}")) {
            throw new IllegalArgumentException(
                    "OpenDART stock code must contain exactly 6 non-whitespace characters");
        }
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
