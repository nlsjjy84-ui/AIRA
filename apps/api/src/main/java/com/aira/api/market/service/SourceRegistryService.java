package com.aira.api.market.service;

import com.aira.api.market.domain.Source;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.repository.SourceRegistrationStore;
import com.aira.api.market.repository.SourceRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SourceRegistryService {
    private final SourceRegistrationStore registrations;
    private final SourceRepository sources;

    public SourceRegistryService(SourceRegistrationStore registrations, SourceRepository sources) {
        this.registrations = registrations;
        this.sources = sources;
    }

    @Transactional
    public Source registerOrReuse(SourceRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("Source registration is required");
        }
        var sourceId = registrations.registerOrGetId(registration);
        return sources.findById(sourceId)
                .orElseThrow(() -> new IllegalStateException("Registered source was not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Source> find(SourceType sourceType, String externalKey) {
        if (sourceType == null || externalKey == null || externalKey.isBlank()) {
            throw new IllegalArgumentException("Source identity is required");
        }
        return sources.findBySourceTypeAndExternalKey(sourceType, externalKey.trim());
    }
}
