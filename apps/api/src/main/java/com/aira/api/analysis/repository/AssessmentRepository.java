package com.aira.api.analysis.repository;

import com.aira.api.analysis.domain.Assessment;
import com.aira.api.analysis.domain.AssessmentStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {
    Optional<Assessment> findByEvent_IdAndAnalysisVersionAndInputFingerprint(
            UUID eventId, String analysisVersion, byte[] inputFingerprint);

    List<Assessment> findAllByEvent_IdAndStatus(UUID eventId, AssessmentStatus status);
}
