package com.aira.api.market.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.EntityType;
import com.aira.api.market.domain.MarketEntity;
import com.aira.api.market.domain.Source;
import com.aira.api.market.domain.StatisticalAdjustment;
import com.aira.api.market.domain.StatisticalFrequency;
import com.aira.api.market.domain.StatisticalMetric;
import com.aira.api.market.domain.StatisticalSeries;
import com.aira.api.market.domain.StatisticalValueKind;
import com.aira.api.market.repository.MarketEntityRepository;
import com.aira.api.market.repository.SourceRepository;
import com.aira.api.market.repository.StatisticalSeriesRegistrationStore;
import com.aira.api.market.repository.StatisticalSeriesRepository;
import com.aira.api.market.repository.StatisticalSeriesSourceMappingRegistrationStore;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StatisticalSeriesRegistryServiceTests {
    @Mock private MarketEntityRepository entities;
    @Mock private SourceRepository sources;
    @Mock private StatisticalSeriesRepository series;
    @Mock private StatisticalSeriesRegistrationStore seriesStore;
    @Mock private StatisticalSeriesSourceMappingRegistrationStore mappingStore;
    @Mock private MarketEntity subject;
    @Mock private StatisticalSeries statisticalSeries;
    @Mock private Source source;

    private StatisticalSeriesRegistryService service;

    @BeforeEach
    void setUp() {
        service = new StatisticalSeriesRegistryService(
                entities, sources, series, seriesStore, mappingStore);
    }

    @Test
    void registersRealGdpOnlyForActiveCountrySubject() {
        UUID countryId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();
        var registration = series(countryId);
        when(entities.findById(countryId)).thenReturn(Optional.of(subject));
        when(subject.isActive()).thenReturn(true);
        when(subject.getEntityType()).thenReturn(EntityType.COUNTRY);
        when(seriesStore.registerOrGetId(registration)).thenReturn(seriesId);

        assertEquals(seriesId, service.registerSeries(registration));
        verify(seriesStore).registerOrGetId(registration);
    }

    @Test
    void rejectsRealGdpForNonCountrySubjectBeforeStore() {
        UUID subjectId = UUID.randomUUID();
        var registration = series(subjectId);
        when(entities.findById(subjectId)).thenReturn(Optional.of(subject));
        when(subject.isActive()).thenReturn(true);
        when(subject.getEntityType()).thenReturn(EntityType.COMPANY);

        assertThrows(IllegalArgumentException.class,
                () -> service.registerSeries(registration));
        verify(seriesStore, never()).registerOrGetId(registration);
    }

    @Test
    void rejectsInactiveSeriesSubjectBeforeStore() {
        UUID subjectId = UUID.randomUUID();
        var registration = series(subjectId);
        when(entities.findById(subjectId)).thenReturn(Optional.of(subject));
        when(subject.isActive()).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.registerSeries(registration));
        verify(seriesStore, never()).registerOrGetId(registration);
    }

    @Test
    void registersMappingOnlyForActiveSeriesAndSource() {
        UUID seriesId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        var registration = mapping(seriesId, sourceId);
        when(series.findById(seriesId)).thenReturn(Optional.of(statisticalSeries));
        when(statisticalSeries.isActive()).thenReturn(true);
        when(sources.findById(sourceId)).thenReturn(Optional.of(source));
        when(source.isActive()).thenReturn(true);
        when(mappingStore.registerOrGetId(registration)).thenReturn(mappingId);

        assertEquals(mappingId, service.registerSourceMapping(registration));
    }
    @Test
    void rejectsInactiveSourceBeforeMappingStore() {
        UUID seriesId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        var registration = mapping(seriesId, sourceId);
        when(series.findById(seriesId)).thenReturn(Optional.of(statisticalSeries));
        when(statisticalSeries.isActive()).thenReturn(true);
        when(sources.findById(sourceId)).thenReturn(Optional.of(source));
        when(source.isActive()).thenReturn(false);

        assertThrows(IllegalStateException.class,
                () -> service.registerSourceMapping(registration));
        verify(mappingStore, never()).registerOrGetId(registration);
    }

    @Test
    void rejectsMissingSeriesBeforeMappingStore() {
        UUID seriesId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        var registration = mapping(seriesId, sourceId);
        when(series.findById(seriesId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.registerSourceMapping(registration));
        verify(mappingStore, never()).registerOrGetId(registration);
    }

    @Test
    void registrationRecordsRejectMissingIdentityAndBlankProviderMetadata() {
        assertThrows(IllegalArgumentException.class,
                () -> new StatisticalSeriesRegistration(null, StatisticalMetric.REAL_GDP,
                        StatisticalFrequency.QUARTERLY,
                        StatisticalAdjustment.SEASONALLY_ADJUSTED,
                        StatisticalValueKind.LEVEL));
        assertThrows(IllegalArgumentException.class,
                () -> new StatisticalSeriesSourceMappingRegistration(
                        UUID.randomUUID(), UUID.randomUUID(), " ", "Series", "Item",
                        "Q", "십억원", "StatisticItemList/200Y104"));
    }

    @Test
    void rejectsNullRegistrationsAtServiceBoundary() {
        assertThrows(IllegalArgumentException.class, () -> service.registerSeries(null));
        assertThrows(IllegalArgumentException.class, () -> service.registerSourceMapping(null));
    }

    @Test
    void rejectsProviderMetadataWithSurroundingWhitespace() {
        assertThrows(IllegalArgumentException.class,
                () -> new StatisticalSeriesSourceMappingRegistration(
                        UUID.randomUUID(), UUID.randomUUID(),
                        " StatisticSearch:200Y104:1400:-:-:-:Q",
                        "Series", "Item", "Q", "십억원",
                        "StatisticItemList/json/kr/200Y104/1/1000"));
    }

    private static StatisticalSeriesRegistration series(UUID countryId) {
        return new StatisticalSeriesRegistration(
                countryId,
                StatisticalMetric.REAL_GDP,
                StatisticalFrequency.QUARTERLY,
                StatisticalAdjustment.SEASONALLY_ADJUSTED,
                StatisticalValueKind.LEVEL);
    }

    private static StatisticalSeriesSourceMappingRegistration mapping(
            UUID seriesId, UUID sourceId) {
        return new StatisticalSeriesSourceMappingRegistration(
                seriesId,
                sourceId,
                "StatisticSearch:200Y104:1400:-:-:-:Q",
                "2.1.2.1.2. 경제활동별 GDP 및 GNI(계절조정, 실질, 분기)",
                "국내총생산(시장가격, GDP)",
                "Q",
                "십억원",
                "StatisticItemList/json/kr/200Y104/1/1000");
    }
}
