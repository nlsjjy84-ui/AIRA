package com.aira.api.market.repository;

import com.aira.api.market.domain.StatisticalSeriesSourceMapping;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StatisticalSeriesSourceMappingRepository
        extends JpaRepository<StatisticalSeriesSourceMapping, UUID> {
}
