package com.aira.api.market.query;

public class CompanyFinancialFactsQueryException extends RuntimeException {
    private final Category category;

    public CompanyFinancialFactsQueryException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public Category category() {
        return category;
    }

    public enum Category {
        COMPANY_NOT_FOUND,
        FACTS_NOT_FOUND,
        UNSUPPORTED_PREDICATE,
        INCONSISTENT_PROVENANCE
    }
}
