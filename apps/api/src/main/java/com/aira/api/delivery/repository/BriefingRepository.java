package com.aira.api.delivery.repository;

import com.aira.api.delivery.domain.Briefing;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BriefingRepository extends JpaRepository<Briefing, UUID> {}
