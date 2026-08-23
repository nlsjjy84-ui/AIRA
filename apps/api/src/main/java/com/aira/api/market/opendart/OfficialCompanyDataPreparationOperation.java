package com.aira.api.market.opendart;

import com.aira.api.market.domain.FactPredicate;
import com.aira.api.market.service.CompanyEntityBootstrapCommand;
import com.aira.api.market.service.CompanyEntityBootstrapOperation;
import com.aira.api.market.service.EntityExternalIdentifierRegistryService;
import com.aira.api.market.service.ExternalIdentifierKey;
import com.aira.api.market.service.ExternalIdentifierRegistration;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfficialCompanyDataPreparationOperation {
    private final OpenDartCompanyDirectoryClient directoryClient;
    private final OpenDartCompanyProfileClient profileClient;
    private final CompanyEntityBootstrapOperation companies;
    private final EntityExternalIdentifierRegistryService identifiers;
    private final OpenDartAnnualCfsAdapter annualCfs;

    public OfficialCompanyDataPreparationOperation(OpenDartCompanyDirectoryClient directoryClient,
            OpenDartCompanyProfileClient profileClient, CompanyEntityBootstrapOperation companies,
            EntityExternalIdentifierRegistryService identifiers, OpenDartAnnualCfsAdapter annualCfs) {
        this.directoryClient = directoryClient;
        this.profileClient = profileClient;
        this.companies = companies;
        this.identifiers = identifiers;
        this.annualCfs = annualCfs;
    }

    @Transactional
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
            var key = new ExternalIdentifierKey("OPENDART", "CORP_CODE", record.corpCode());
            UUID entityId = identifiers.findEntity(key).map(entity -> entity.getId()).orElseGet(() -> {
                var created = companies.create(new CompanyEntityBootstrapCommand(record.corpName(), "KR"));
                identifiers.registerOrReuse(new ExternalIdentifierRegistration(created.entityId(), key));
                return created.entityId();
            });
            annualCfs.ingestFiling(new OpenDartAnnualCfsContext(record.corpCode(), businessYear,
                    "11011", "CFS", profile.fiscalYearEndMonth()),
                    List.of(FactPredicate.REVENUE, FactPredicate.OPERATING_INCOME));
            return new PreparedCompany(entityId, record.corpCode(), record.stockCode(),
                    record.corpName(), profile.fiscalYearEndMonth(), businessYear);
        }).toList();
    }

    public record PreparedCompany(UUID entityId, String corpCode, String stockCode,
            String companyName, int fiscalYearEndMonth, int businessYear) {}
}
