package com.aira.api.market.repository;

import com.aira.api.market.domain.EventEvidence;
import com.aira.api.market.domain.EventEvidenceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventEvidenceRepository extends JpaRepository<EventEvidence, EventEvidenceId> {}
