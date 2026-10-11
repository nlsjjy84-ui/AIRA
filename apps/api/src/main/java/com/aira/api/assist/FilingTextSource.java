package com.aira.api.assist;

/** Reads the plain text of one official filing. Failures are IllegalStateException with a short code. */
public interface FilingTextSource {
    /** Codes: NO_KEY, NOT_FOUND, RATE, TOO_BIG, FAILED. */
    String fetch(String receiptNumber);
}
