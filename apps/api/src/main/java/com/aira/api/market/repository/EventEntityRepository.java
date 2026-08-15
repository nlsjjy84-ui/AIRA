package com.aira.api.market.repository;

import com.aira.api.market.domain.EventEntity;
import com.aira.api.market.domain.EventEntityId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventEntityRepository extends JpaRepository<EventEntity, EventEntityId> {}
