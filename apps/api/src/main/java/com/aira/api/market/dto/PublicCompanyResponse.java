package com.aira.api.market.dto;

import java.util.UUID;

public record PublicCompanyResponse(UUID companyId, String canonicalName, String countryCode) {}
