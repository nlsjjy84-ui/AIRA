package com.aira.api.market.query;

public class PublicCompanyCatalogException extends RuntimeException {
    public enum Category { COMPANY_NOT_FOUND, PERIODS_NOT_FOUND }

    private final Category category;

    public PublicCompanyCatalogException(Category category, String message) {
        super(message);
        this.category = category;
    }

    public Category category() { return category; }
}
