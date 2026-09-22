package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import com.aira.api.market.domain.Evidence;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class EcosRealGdpCanonicalEvidenceTests {
    @Test void rejectsRegisteredEvidenceWithWrongLocator() {
        var result = result();
        var collected = OffsetDateTime.parse("2026-09-22T00:00:00Z");
        var expected = EcosRealGdpEvidenceSnapshotFactory.create(result, collected);
        Evidence evidence = Mockito.mock(Evidence.class);
        when(evidence.getEvidenceType()).thenReturn(expected.evidenceType());
        when(evidence.getExternalId()).thenReturn(expected.externalId());
        when(evidence.getOriginalUrl()).thenReturn(expected.originalUrl());
        when(evidence.getTitle()).thenReturn(expected.title());
        when(evidence.getContentHash()).thenReturn(expected.contentHash());
        when(evidence.getLocator()).thenReturn("wrong-locator");
        when(evidence.getPublishedAt()).thenReturn(expected.publishedAt());
        when(evidence.getRevision()).thenReturn(expected.revision());
        assertThrows(IllegalStateException.class,
            () -> EcosRealGdpFactIngestionService.requireCanonicalEvidence(evidence, result, collected));
    }
    private static EcosRealGdpObservationResult result() {
        var row=new EcosStatisticSearchObservation(EcosRealGdpContract.STAT_CODE,EcosRealGdpContract.STAT_NAME,
            EcosRealGdpContract.ITEM_CODE1,EcosRealGdpContract.ITEM_NAME1,null,null,null,null,null,null,
            EcosRealGdpContract.UNIT_NAME,null,"2026Q1","596692.8",new BigDecimal("596692.8"));
        return new EcosRealGdpObservationResult("2026Q1","2026Q1",1,1,1,3,List.of(row));
    }
}
