package com.aira.api.market.opendart;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.service.EntityAliasRegistryService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class OfficialCompanyDataPreparationOperation {
    private static final Logger log = LoggerFactory.getLogger(OfficialCompanyDataPreparationOperation.class);

    // Common colloquial/legacy names that differ from OpenDART's official canonical_name,
    // so users searching by the name they actually use (e.g. "현대차") still find the company.
    private static final Map<String, List<String>> KNOWN_STOCK_CODE_ALIASES = Map.of(
            "005380", List.of("현대차"),
            "000270", List.of("기아자동차", "기아차"));

    private final OpenDartCompanyDirectoryClient directoryClient;
    private final OpenDartCompanyProfileClient profileClient;
    private final OpenDartFilingPersistence persistence;
    private final OpenDartAnnualCfsAdapter annualCfs;
    private final EntityAliasRegistryService aliases;

    public OfficialCompanyDataPreparationOperation(OpenDartCompanyDirectoryClient directoryClient,
            OpenDartCompanyProfileClient profileClient, OpenDartFilingPersistence persistence,
            OpenDartAnnualCfsAdapter annualCfs, EntityAliasRegistryService aliases) {
        this.directoryClient = directoryClient;
        this.profileClient = profileClient;
        this.persistence = persistence;
        this.annualCfs = annualCfs;
        this.aliases = aliases;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public List<PreparedCompany> prepareByStockCodes(List<String> stockCodes, int businessYear) {
        if (stockCodes == null || stockCodes.size() < 2 || stockCodes.stream().distinct().count() != stockCodes.size()) {
            throw new IllegalArgumentException("At least two distinct stock codes are required");
        }
        var directory = directoryClient.fetch();
        return stockCodes.stream().map(stockCode -> {
            var record = directory.findByStockCode(stockCode)
                    .orElseThrow(() -> new IllegalStateException("Official company was not found for stock code " + stockCode));
            var profile = profileClient.fetch(record.corpCode());
            if (!record.corpCode().equals(profile.corpCode())) throw new IllegalStateException("OpenDART company identity is inconsistent");
            var filing = annualCfs.prepare(new OpenDartAnnualCfsContext(record.corpCode(), businessYear,
                    "11011", "CFS"),
                    List.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME));
            UUID entityId = persistence.persist(filing, record).companyId();
            var knownAliases = KNOWN_STOCK_CODE_ALIASES.getOrDefault(stockCode, List.of());
            log.info("Stock code {} maps to {} known alias(es): {}", stockCode, knownAliases.size(), knownAliases);
            knownAliases.forEach(alias -> {
                try {
                    aliases.registerOrReuse(entityId, alias);
                    log.info("Registered alias '{}' for entity {} (stock code {})", alias, entityId, stockCode);
                } catch (RuntimeException ex) {
                    log.error("Failed to register alias '{}' for entity {} (stock code {}): {}",
                            alias, entityId, stockCode, ex.toString(), ex);
                }
            });
            return new PreparedCompany(entityId, record.corpCode(), record.stockCode(),
                    record.corpName(), profile.fiscalYearEndMonth(), businessYear);
        }).toList();
    }

    public record PreparedCompany(UUID entityId, String corpCode, String stockCode,
            String companyName, int fiscalYearEndMonth, int businessYear) {}
}
