package com.aira.api.market.dto;

/** Keep absence, incompleteness, conflict and failed verification distinct; STALE needs an approved age rule before emission. */
public enum CanonicalDataState {
    AVAILABLE, NO_DATA, PARTIAL, STALE, CONFLICTING, BLOCKED, UNSUPPORTED, UNAVAILABLE
}
