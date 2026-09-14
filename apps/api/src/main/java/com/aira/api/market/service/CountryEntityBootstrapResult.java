package com.aira.api.market.service;

import java.util.UUID;

public record CountryEntityBootstrapResult(
        UUID entityId,
        String canonicalKey,
        String canonicalName,
        String countryCode) {}
