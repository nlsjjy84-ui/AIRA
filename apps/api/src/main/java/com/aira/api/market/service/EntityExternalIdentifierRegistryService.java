package com.aira.api.market.service;

import com.aira.api.market.domain.EntityExternalIdentifier;
import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.exception.ExternalIdentifierConflictException;
import com.aira.api.market.repository.EntityExternalIdentifierRegistrationStore;
import com.aira.api.market.repository.EntityExternalIdentifierRepository;
import com.aira.api.market.repository.MarketEntityRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntityExternalIdentifierRegistryService {
    private final MarketEntityRepository entities;
    private final EntityExternalIdentifierRepository identifiers;
    private final EntityExternalIdentifierRegistrationStore registrations;

    public EntityExternalIdentifierRegistryService(MarketEntityRepository entities,
            EntityExternalIdentifierRepository identifiers,
            EntityExternalIdentifierRegistrationStore registrations) {
        this.entities = entities;
        this.identifiers = identifiers;
        this.registrations = registrations;
    }

    @Transactional
    public EntityExternalIdentifier registerOrReuse(ExternalIdentifierRegistration registration) {
        if (registration == null) {
            throw new IllegalArgumentException("External identifier registration is required");
        }
        MarketEntity entity = entities.findById(registration.entityId())
                .orElseThrow(() -> new IllegalArgumentException("Entity was not found"));
        requireCompatibleEntity(registration.identifier(), entity);

        var stored = registrations.registerOrGet(registration);
        if (!stored.entityId().equals(registration.entityId())) {
            throw new ExternalIdentifierConflictException();
        }
        return identifiers.findById(stored.id())
                .orElseThrow(() -> new IllegalStateException(
                        "Registered external identifier was not found"));
    }

    @Transactional(readOnly = true)
    public Optional<MarketEntity> findEntity(ExternalIdentifierKey key) {
        if (key == null) {
            throw new IllegalArgumentException("External identifier is required");
        }
        return identifiers.findByNamespaceAndIdentifierTypeAndIdentifierValue(
                key.namespace(), key.identifierType(), key.identifierValue())
                .map(EntityExternalIdentifier::getEntity);
    }

    @Transactional(readOnly = true)
    public List<EntityExternalIdentifier> findIdentifiers(UUID entityId) {
        if (entityId == null) {
            throw new IllegalArgumentException("Entity identifier is required");
        }
        return identifiers.findAllByEntity_IdOrderByNamespaceAscIdentifierTypeAscIdentifierValueAsc(
                entityId);
    }

    private static void requireCompatibleEntity(
            ExternalIdentifierKey key, MarketEntity entity) {
        if ("OPENDART".equals(key.namespace()) && "CORP_CODE".equals(key.identifierType())
                && entity.getEntityType() != EntityType.COMPANY) {
            throw new IllegalArgumentException(
                    "OpenDART corp code can only identify a company entity");
        }
    }
}
