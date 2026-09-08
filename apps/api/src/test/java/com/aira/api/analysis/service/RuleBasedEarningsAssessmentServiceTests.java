package com.aira.api.analysis.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.aira.api.analysis.domain.*;
import com.aira.api.analysis.repository.*;
import com.aira.api.market.domain.*;
import com.aira.api.market.repository.*;
import java.lang.reflect.Field;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RuleBasedEarningsAssessmentServiceTests {
    @Test
    void newAssessmentSupersedesTheUniqueCurrentAssessmentForTheLockedEvent() throws Exception {
        EventRepository events = mock(EventRepository.class);
        EvidenceRepository evidence = mock(EvidenceRepository.class);
        EventEvidenceRepository eventEvidence = mock(EventEvidenceRepository.class);
        AssessmentRepository assessments = mock(AssessmentRepository.class);
        AssessmentEvidenceRepository assessmentEvidence = mock(AssessmentEvidenceRepository.class);
        var service = new RuleBasedEarningsAssessmentService(events, evidence, eventEvidence,
                assessments, assessmentEvidence);
        Event event = confirmedEvent();
        Evidence sourceEvidence = evidence(new byte[] {9, 8, 7});
        Assessment current = assessment(event, null);
        set(current, "id", UUID.randomUUID());
        EventEvidence link = EventEvidence.supports(event, sourceEvidence, now());

        when(events.findLockedById(event.getId())).thenReturn(Optional.of(event));
        when(eventEvidence.findById(new EventEvidenceId(event.getId(), sourceEvidence.getId())))
                .thenReturn(Optional.of(link));
        when(evidence.findById(sourceEvidence.getId())).thenReturn(Optional.of(sourceEvidence));
        when(assessments.findByEvent_IdAndAnalysisVersionAndInputFingerprint(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(assessments.findAllByEvent_IdAndStatus(event.getId(), AssessmentStatus.COMPLETED))
                .thenReturn(List.of(current));
        when(assessments.saveAndFlush(any())).thenAnswer(invocation -> {
            Assessment saved = invocation.getArgument(0);
            set(saved, "id", UUID.randomUUID());
            return saved;
        });
        when(assessmentEvidence.existsById(any())).thenReturn(false);

        Assessment result = service.assess(event.getId(), sourceEvidence.getId());

        assertSame(current, result.getSupersedesAssessment());
        verify(events).findLockedById(event.getId());
        verify(assessmentEvidence).saveAndFlush(any(AssessmentEvidence.class));
    }

    @Test
    void candidateEventCannotBeAssessed() throws Exception {
        EventRepository events = mock(EventRepository.class);
        var service = new RuleBasedEarningsAssessmentService(events, mock(EvidenceRepository.class),
                mock(EventEvidenceRepository.class), mock(AssessmentRepository.class),
                mock(AssessmentEvidenceRepository.class));
        Event event = Event.createEarnings("title", now(), now(), new byte[] {1}, now());
        set(event, "id", UUID.randomUUID());
        when(events.findLockedById(event.getId())).thenReturn(Optional.of(event));

        assertThrows(IllegalStateException.class,
                () -> service.assess(event.getId(), UUID.randomUUID()));
    }

    private static Assessment assessment(Event event, Assessment predecessor) {
        return Assessment.completedRule(event, "previous", Importance.MEDIUM, "summary",
                Confidence.MEDIUM, "uncertainty", TimeHorizon.UNSPECIFIED,
                new byte[] {1}, predecessor, now());
    }

    private static Event confirmedEvent() throws Exception {
        Event event = Event.createEarnings("title", now(), now(), new byte[] {1}, now());
        set(event, "id", UUID.randomUUID());
        event.confirm(true, true, now());
        return event;
    }

    private static Evidence evidence(byte[] hash) throws Exception {
        var constructor = Evidence.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Evidence evidence = constructor.newInstance();
        set(evidence, "id", UUID.randomUUID());
        set(evidence, "contentHash", hash);
        return evidence;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.parse("2026-01-01T00:00:00Z");
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
