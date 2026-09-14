package com.aira.api.market.repository;

import com.aira.api.market.domain.FactStatisticalContext;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FactStatisticalContextRepository
        extends JpaRepository<FactStatisticalContext, UUID> {
}
