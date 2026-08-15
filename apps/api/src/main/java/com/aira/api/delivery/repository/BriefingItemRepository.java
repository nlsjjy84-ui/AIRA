package com.aira.api.delivery.repository;

import com.aira.api.delivery.domain.BriefingItem;
import com.aira.api.delivery.domain.BriefingItemId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingItemRepository extends JpaRepository<BriefingItem, BriefingItemId> {}
