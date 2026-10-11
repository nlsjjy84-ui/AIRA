package com.aira.api.assist;

public interface ReadingAssistModel {
    boolean available();
    String providerKey();
    String modelKey();
    /** Returns plain text. Throws RuntimeException on any provider failure. */
    String complete(String prompt);
}
