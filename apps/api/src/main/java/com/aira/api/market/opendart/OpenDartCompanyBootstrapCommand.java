package com.aira.api.market.opendart;

import java.util.UUID;

public record OpenDartCompanyBootstrapCommand(
        UUID entityId,
        String corpCode,
        OpenDartCompanyBootstrapMode mode) {

    public OpenDartCompanyBootstrapCommand(UUID entityId, String corpCode) {
        this(entityId, corpCode, OpenDartCompanyBootstrapMode.DRY_RUN);
    }

    public OpenDartCompanyBootstrapCommand {
        if (entityId == null) {
            throw new IllegalArgumentException("Company entity identifier is required");
        }
        if (corpCode == null || !corpCode.matches("[0-9]{8}")) {
            throw new IllegalArgumentException("OpenDART corp code must contain exactly 8 digits");
        }
        if (mode == null) {
            throw new IllegalArgumentException("Bootstrap mode is required");
        }
    }
}
