package com.aira.api.analysis.repository;

import com.aira.api.analysis.domain.Assessment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {}
