package com.aira.api.market.repository;

import com.aira.api.market.domain.MarketEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketEntityRepository extends JpaRepository<MarketEntity, UUID> {}
