package com.aira.api.market.service;

import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.repository.MarketEntityRepository;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyEntityBootstrapOperation {
    private final MarketEntityRepository entities;
    private final Clock clock;
    private final Supplier<UUID> opaqueIdentityGenerator;

    @Autowired
    public CompanyEntityBootstrapOperation(MarketEntityRepository entities) {
        this(entities, Clock.systemUTC(), UUID::randomUUID);
    }

    CompanyEntityBootstrapOperation(MarketEntityRepository entities, Clock clock,
            Supplier<UUID> opaqueIdentityGenerator) {
        this.entities = entities;
        this.clock = clock;
        this.opaqueIdentityGenerator = opaqueIdentityGenerator;
    }

    @Transactional
    public CompanyEntityBootstrapResult create(CompanyEntityBootstrapCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("Company bootstrap command is required");
        }
        MarketEntity company = MarketEntity.company(command.canonicalName(), command.countryCode(),
                opaqueIdentityGenerator.get(), OffsetDateTime.now(clock));
        MarketEntity stored = entities.save(company);
        return new CompanyEntityBootstrapResult(stored.getId(), stored.getCanonicalKey(),
                stored.getCanonicalName(), stored.getCountryCode());
    }
}
