package com.aira.api.market.ecos;

import com.aira.api.market.domain.Source;
import com.aira.api.market.domain.StatisticalAdjustment;
import com.aira.api.market.domain.StatisticalFrequency;
import com.aira.api.market.domain.StatisticalMetric;
import com.aira.api.market.domain.StatisticalValueKind;
import com.aira.api.market.service.KoreaCountryBootstrapOperation;
import com.aira.api.market.service.SourceRegistryService;
import com.aira.api.market.service.StatisticalSeriesRegistration;
import com.aira.api.market.service.StatisticalSeriesRegistryService;
import com.aira.api.market.service.StatisticalSeriesSourceMappingRegistration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EcosRealGdpSeriesBindingService {
    static final String PROVIDER_BINDING_KEY =
            "StatisticSearch:200Y104:1400:-:-:-:Q";
    static final String METADATA_LOCATOR =
            "StatisticItemList/json/kr/200Y104/1/1000";

    private final KoreaCountryBootstrapOperation countries;
    private final SourceRegistryService sources;
    private final StatisticalSeriesRegistryService series;

    public EcosRealGdpSeriesBindingService(
            KoreaCountryBootstrapOperation countries,
            SourceRegistryService sources,
            StatisticalSeriesRegistryService series) {
        if (countries == null || sources == null || series == null) {
            throw new IllegalArgumentException("REAL_GDP series binding dependencies are required");
        }
        this.countries = countries;
        this.sources = sources;
        this.series = series;
    }

    @Transactional
    public EcosRealGdpSeriesBindingResult registerOrReuse() {
        var country = countries.registerOrReuse();
        Source source = sources.registerOrReuse(EcosOfficialSourceContract.BOK_ECOS);
        EcosOfficialSourceContract.requireCanonicalBokEcos(source);

        UUID seriesId = series.registerSeries(new StatisticalSeriesRegistration(
                country.entityId(),
                StatisticalMetric.REAL_GDP,
                StatisticalFrequency.QUARTERLY,
                StatisticalAdjustment.SEASONALLY_ADJUSTED,
                StatisticalValueKind.LEVEL));

        UUID mappingId = series.registerSourceMapping(
                new StatisticalSeriesSourceMappingRegistration(
                        seriesId,
                        source.getId(),
                        PROVIDER_BINDING_KEY,
                        EcosRealGdpContract.STAT_NAME,
                        EcosRealGdpContract.ITEM_NAME1,
                        EcosRealGdpContract.CYCLE,
                        EcosRealGdpContract.UNIT_NAME,
                        METADATA_LOCATOR));

        return new EcosRealGdpSeriesBindingResult(
                country.entityId(), source.getId(), seriesId, mappingId);
    }
}
