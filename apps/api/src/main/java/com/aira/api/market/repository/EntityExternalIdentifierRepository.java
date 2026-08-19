package com.aira.api.market.repository;

import com.aira.api.market.domain.EntityExternalIdentifier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntityExternalIdentifierRepository
        extends JpaRepository<EntityExternalIdentifier, UUID> {
    Optional<EntityExternalIdentifier> findByNamespaceAndIdentifierTypeAndIdentifierValue(
            String namespace, String identifierType, String identifierValue);

    List<EntityExternalIdentifier> findAllByEntity_IdOrderByNamespaceAscIdentifierTypeAscIdentifierValueAsc(
            UUID entityId);
}
