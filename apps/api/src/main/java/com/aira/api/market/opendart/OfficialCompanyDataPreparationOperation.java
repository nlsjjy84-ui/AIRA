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
            "000270", List.of("기아자동차", "기아차"),
            "035420", List.of("네이버"));

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
        return stockCodes.stream().map(stockCode -> prepareOne(directory, stockCode, businessYear)).toList();
    }

    /** 상장사 전체 준비 작업이 회사 목록을 한 번만 내려받아 여러 회사에 재사용할 수 있게 연다. */
    public OpenDartCompanyDirectory fetchDirectory() {
        return directoryClient.fetch();
    }

    /** 회사 한 곳의 연결 연간 재무를 공식 공시에서 가져와 저장한다. 실패하면 예외를 던지고 아무것도 남기지 않는다. */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public PreparedCompany prepareOne(OpenDartCompanyDirectory directory, String stockCode, int businessYear) {
        var record = directory.findByStockCode(stockCode)
                .orElseThrow(() -> new IllegalStateException("Official company was not found for stock code " + stockCode));
        var profile = profileClient.fetch(record.corpCode());
        if (!record.corpCode().equals(profile.corpCode())) throw new IllegalStateException("OpenDART company identity is inconsistent");
        OpenDartPreparedFiling filing;
        try {
            filing = annualCfs.prepare(new OpenDartAnnualCfsContext(record.corpCode(), businessYear,
                    "11011", "CFS"),
                    FactPredicate.COLLECTED_COMPANY_FINANCIALS);
        } catch (OpenDartProviderException e) {
            // 자회사가 없어 연결재무제표를 내지 않는 회사는 별도재무제표(OFS)로 받는다. 저장 위치(locator)가 "OFS/..."로
            // 남아 화면과 사실 확인에서 '별도 기준'으로 표시된다. 연결이 있는 회사는 절대 별도로 바꾸지 않는다.
            // 연결 공시 자체가 없을 때(OpenDART 상태 013)만 별도로 간다. 연결은 있는데 계정만 못 찾은 경우는 그대로 건너뛴다.
            if (e.category() != OpenDartProviderException.Category.NO_DATA
                    || e.getMessage() == null || !e.getMessage().endsWith("status 013")) throw e;
            log.info("Stock code {} has no consolidated annual statements for {}; trying separate (OFS)", stockCode, businessYear);
            filing = annualCfs.prepare(new OpenDartAnnualCfsContext(record.corpCode(), businessYear,
                    "11011", "OFS"),
                    FactPredicate.COLLECTED_COMPANY_FINANCIALS);
        }
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
    }

    public record PreparedCompany(UUID entityId, String corpCode, String stockCode,
            String companyName, int fiscalYearEndMonth, int businessYear) {}
}
