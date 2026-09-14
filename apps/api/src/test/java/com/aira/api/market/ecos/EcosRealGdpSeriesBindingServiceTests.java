package com.aira.api.market.ecos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aira.api.market.domain.Source;
import com.aira.api.market.domain.SourceType;
import com.aira.api.market.domain.StatisticalAdjustment;
import com.aira.api.market.domain.StatisticalFrequency;
import com.aira.api.market.domain.StatisticalMetric;
import com.aira.api.market.domain.StatisticalValueKind;
import com.aira.api.market.service.CountryEntityBootstrapResult;
import com.aira.api.market.service.KoreaCountryBootstrapOperation;
import com.aira.api.market.service.SourceRegistryService;
import com.aira.api.market.service.StatisticalSeriesRegistration;
import com.aira.api.market.service.StatisticalSeriesRegistryService;
import com.aira.api.market.service.StatisticalSeriesSourceMappingRegistration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EcosRealGdpSeriesBindingServiceTests {
    @Mock KoreaCountryBootstrapOperation countries;
    @Mock SourceRegistryService sources;
    @Mock StatisticalSeriesRegistryService series;
    @Mock Source source;

    private EcosRealGdpSeriesBindingService service;

    @BeforeEach
    void setUp() {
        service = new EcosRealGdpSeriesBindingService(countries, sources, series);
    }

    @Test
    void bindsFrozenKoreaRealGdpContractEndToEnd() {
        UUID countryId = UUID.randomUUID();
        UUID sourceId = UUID.randomUUID();
        UUID seriesId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();

        when(countries.registerOrReuse()).thenReturn(new CountryEntityBootstrapResult(
                countryId, "COUNTRY:KR", "Republic of Korea", "KR"));
        stubCanonicalSource(sourceId);
        when(sources.registerOrReuse(EcosOfficialSourceContract.BOK_ECOS)).thenReturn(source);
        when(series.registerSeries(org.mockito.ArgumentMatchers.any())).thenReturn(seriesId);
        when(series.registerSourceMapping(org.mockito.ArgumentMatchers.any())).thenReturn(mappingId);

        var result = service.registerOrReuse();

        assertEquals(countryId, result.countryEntityId());
        assertEquals(sourceId, result.sourceId());
        assertEquals(seriesId, result.statisticalSeriesId());
        assertEquals(mappingId, result.sourceMappingId());

        var seriesCaptor = ArgumentCaptor.forClass(StatisticalSeriesRegistration.class);
        verify(series).registerSeries(seriesCaptor.capture());
        var seriesRegistration = seriesCaptor.getValue();
        assertEquals(countryId, seriesRegistration.subjectEntityId());
        assertEquals(StatisticalMetric.REAL_GDP, seriesRegistration.metric());
        assertEquals(StatisticalFrequency.QUARTERLY, seriesRegistration.frequency());
        assertEquals(StatisticalAdjustment.SEASONALLY_ADJUSTED, seriesRegistration.adjustment());
        assertEquals(StatisticalValueKind.LEVEL, seriesRegistration.valueKind());

        var mappingCaptor = ArgumentCaptor.forClass(
                StatisticalSeriesSourceMappingRegistration.class);
        verify(series).registerSourceMapping(mappingCaptor.capture());
        var mapping = mappingCaptor.getValue();
        assertEquals(seriesId, mapping.statisticalSeriesId());
        assertEquals(sourceId, mapping.sourceId());
        assertEquals(EcosRealGdpSeriesBindingService.PROVIDER_BINDING_KEY,
                mapping.providerBindingKey());
        assertEquals(EcosRealGdpContract.STAT_NAME, mapping.providerSeriesName());
        assertEquals(EcosRealGdpContract.ITEM_NAME1, mapping.providerItemName());
        assertEquals(EcosRealGdpContract.CYCLE, mapping.providerFrequencyCode());
        assertEquals(EcosRealGdpContract.UNIT_NAME, mapping.providerUnitName());
        assertEquals(EcosRealGdpSeriesBindingService.METADATA_LOCATOR,
                mapping.metadataLocator());
        verify(sources).registerOrReuse(EcosOfficialSourceContract.BOK_ECOS);
    }

    @Test
    void sourceDriftBlocksBeforeSeriesRegistration() {
        UUID sourceId = UUID.randomUUID();
        when(countries.registerOrReuse()).thenReturn(new CountryEntityBootstrapResult(
                UUID.randomUUID(), "COUNTRY:KR", "Republic of Korea", "KR"));
        when(source.getId()).thenReturn(sourceId);
        when(source.getSourceType()).thenReturn(SourceType.GOVERNMENT);
        when(source.getExternalKey()).thenReturn("BOK_ECOS");
        when(source.getName()).thenReturn("Wrong name");
        when(sources.registerOrReuse(EcosOfficialSourceContract.BOK_ECOS)).thenReturn(source);

        assertThrows(IllegalStateException.class, () -> service.registerOrReuse());
        verify(series, never()).registerSeries(org.mockito.ArgumentMatchers.any());
        verify(series, never()).registerSourceMapping(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void constructorRejectsMissingDependencies() {
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRealGdpSeriesBindingService(null, sources, series));
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRealGdpSeriesBindingService(countries, null, series));
        assertThrows(IllegalArgumentException.class,
                () -> new EcosRealGdpSeriesBindingService(countries, sources, null));
    }

    private void stubCanonicalSource(UUID sourceId) {
        when(source.getId()).thenReturn(sourceId);
        when(source.getSourceType()).thenReturn(SourceType.GOVERNMENT);
        when(source.getExternalKey()).thenReturn("BOK_ECOS");
        when(source.getName()).thenReturn("Bank of Korea ECOS");
        when(source.getCanonicalDomain()).thenReturn("ecos.bok.or.kr");
        when(source.isActive()).thenReturn(true);
    }
}
