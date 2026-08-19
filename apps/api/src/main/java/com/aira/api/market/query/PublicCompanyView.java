package com.aira.api.market.query;

import java.util.UUID;

public record PublicCompanyView(UUID companyId, String canonicalName, String countryCode) {}
