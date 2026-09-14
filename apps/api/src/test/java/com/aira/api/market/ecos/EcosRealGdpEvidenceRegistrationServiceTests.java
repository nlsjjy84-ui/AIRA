package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.Source;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.ingestion.EvidenceRegistration;
import com.aira.api.market.repository.EvidenceRegistrationStore;
import com.aira.api.market.service.SourceRegistryService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EcosRealGdpEvidenceRegistrationServiceTests {
    @Mock SourceRegistryService sources;
    @Mock EvidenceRegistrationStore evidences;
    @Mock Source source;
    private EcosRealGdpEvidenceRegistrationService service;

    @BeforeEach
    void setUp() {
        service = new EcosRealGdpEvidenceRegistrationService(sources, evidences);
    }

    @Test
    void registersOnlyFrozenBokEcosSourceAndSnapshot() {
        UUID sourceId = UUID.randomUUID();
        UUID evidenceId = UUID.randomUUID();
        stubCanonicalSource(sourceId);
        when(sources.registerOrReuse(EcosOfficialSourceContract.BOK_ECOS))
                .thenReturn(source);
        when(evidences.registerOrGetId(any(Source.class), any(EvidenceRegistration.class)))
                .thenReturn(evidenceId);

        UUID actual = service.register(result("596692.8"),
                OffsetDateTime.parse("2026-09-12T00:00:00Z"));

        assertEquals(evidenceId, actual);
        verify(sources).registerOrReuse(EcosOfficialSourceContract.BOK_ECOS);
        var captor = ArgumentCaptor.forClass(EvidenceRegistration.class);
        verify(evidences).registerOrGetId(org.mockito.Mockito.same(source), captor.capture());
        assertEquals("BOK_ECOS:StatisticSearch:v1:kr:200Y104:Q:2026Q1:2026Q1:1400:-:-:-",
                captor.getValue().externalId());
        assertEquals(1, captor.getValue().revision());
    }
    @Test
    void sourceRegistryDriftBlocksBeforeEvidenceRegistration() {
        UUID sourceId = UUID.randomUUID();
        when(source.getId()).thenReturn(sourceId);
        when(source.getSourceType()).thenReturn(SourceType.GOVERNMENT);
        when(source.getExternalKey()).thenReturn("BOK_ECOS");
        when(source.getName()).thenReturn("Wrong name");
        when(sources.registerOrReuse(EcosOfficialSourceContract.BOK_ECOS))
                .thenReturn(source);

        assertThrows(IllegalStateException.class,
                () -> service.register(result("596692.8"),
                        OffsetDateTime.parse("2026-09-12T00:00:00Z")));
        verify(evidences, never()).registerOrGetId(any(), any());
    }

    @Test
    void constructorRejectsMissingDependencies() {
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRealGdpEvidenceRegistrationService(null, evidences));
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRealGdpEvidenceRegistrationService(sources, null));
    }
    private void stubCanonicalSource(UUID id) {
        when(source.getId()).thenReturn(id);
        when(source.getSourceType()).thenReturn(SourceType.GOVERNMENT);
        when(source.getExternalKey()).thenReturn("BOK_ECOS");
        when(source.getName()).thenReturn("Bank of Korea ECOS");
        when(source.getCanonicalDomain()).thenReturn("ecos.bok.or.kr");
        when(source.isActive()).thenReturn(true);
    }

    private static EcosRealGdpObservationResult result(String value) {
        var observation = new EcosStatisticSearchObservation(
                EcosRealGdpContract.STAT_CODE,
                EcosRealGdpContract.STAT_NAME,
                EcosRealGdpContract.ITEM_CODE1,
                EcosRealGdpContract.ITEM_NAME1,
                null, null, null, null, null, null,
                EcosRealGdpContract.UNIT_NAME,
                null,
                "2026Q1",
                value,
                new BigDecimal(value));
        return new EcosRealGdpObservationResult(
                "2026Q1", "2026Q1", 1, 1, 1, 2, List.of(observation));
    }
}
