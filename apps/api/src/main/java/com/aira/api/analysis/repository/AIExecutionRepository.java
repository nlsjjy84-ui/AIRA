package com.aira.api.analysis.repository;

import com.aira.api.analysis.domain.AIExecution;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AIExecutionRepository extends JpaRepository<AIExecution, UUID> {}
