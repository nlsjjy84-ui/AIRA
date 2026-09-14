package com.aira.api.market.service;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.CountryEntityRegistrationStore;
import com.aira.api.market.repository.MarketEntityRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KoreaCountryBootstrapOperation {
    static final CountryEntityRegistration KOREA =
            new CountryEntityRegistration("Republic of Korea", "KR");

    private final CountryEntityRegistrationStore registrations;
    private final MarketEntityRepository entities;

    public KoreaCountryBootstrapOperation(CountryEntityRegistrationStore registrations,
            MarketEntityRepository entities) {
        this.registrations = registrations;
        this.entities = entities;
    }

    @Transactional
    public CountryEntityBootstrapResult registerOrReuse() {
        UUID id = registrations.registerOrGetId(KOREA);
        MarketEntity entity = entities.findById(id)
                .orElseThrow(() -> new IllegalStateException(
                        "Registered Korea country entity was not found"));
        requireCanonicalKorea(entity);
        return new CountryEntityBootstrapResult(entity.getId(), entity.getCanonicalKey(),
                entity.getCanonicalName(), entity.getCountryCode());
    }

    private static void requireCanonicalKorea(MarketEntity entity) {
        if (entity.getEntityType() != EntityType.COUNTRY
                || !KOREA.canonicalKey().equals(entity.getCanonicalKey())
                || !KOREA.canonicalName().equals(entity.getCanonicalName())
                || !KOREA.countryCode().equals(entity.getCountryCode())
                || entity.getMarketCode() != null
                || entity.getSymbol() != null
                || !entity.isActive()) {
            throw new IllegalStateException(
                    "Korea country entity conflicts with AIRA canonical identity contract");
        }
    }
}
