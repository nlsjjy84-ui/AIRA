package com.aira.api.analysis.repository;

import com.aira.api.analysis.domain.Assessment;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
    Optional<Assessment> findByEvent_IdAndAnalysisVersionAndInputFingerprint(
            UUID eventId, String analysisVersion, byte[] inputFingerprint);
}
