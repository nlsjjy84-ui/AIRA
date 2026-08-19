package com.aira.api.market.repository;

import com.aira.api.market.domain.Evidence;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {
    Optional<Evidence> findBySourceIdAndExternalIdAndRevision(
            UUID sourceId, String externalId, int revision);
}
