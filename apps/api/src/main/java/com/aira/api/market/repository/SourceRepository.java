package com.aira.api.market.repository;

import com.aira.api.market.domain.Source;
import com.aira.api.market.domain.SourceType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceRepository extends JpaRepository<Source, UUID> {
    Optional<Source> findBySourceTypeAndExternalKey(SourceType sourceType, String externalKey);
}
