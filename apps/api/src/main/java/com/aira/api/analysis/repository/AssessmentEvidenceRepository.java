package com.aira.api.analysis.repository;

import com.aira.api.analysis.domain.AssessmentEvidence;
import com.aira.api.analysis.domain.AssessmentEvidenceId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentEvidenceRepository
        extends JpaRepository<AssessmentEvidence, AssessmentEvidenceId> {}
