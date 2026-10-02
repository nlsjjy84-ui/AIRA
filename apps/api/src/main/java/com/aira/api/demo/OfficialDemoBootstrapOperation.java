package com.aira.api.demo;

import com.aira.api.analysis.service.OfficialEventAssessmentPreparationOperation;
import com.aira.api.market.opendart.OfficialCompanyDataPreparationOperation;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class OfficialDemoBootstrapOperation {
    public static final List<String> REPRESENTATIVE_STOCK_CODES =
            List.of("000660", "035420", "005930", "035720", "066570", "005380", "000270");
    // 000660 SK하이닉스, 035420 NAVER, 005930 삼성전자, 035720 카카오,
    // 066570 LG전자, 005380 현대차, 000270 기아

    private final OfficialCompanyDataPreparationOperation companyData;
    private final OfficialEventAssessmentPreparationOperation eventAssessments;

    public OfficialDemoBootstrapOperation(OfficialCompanyDataPreparationOperation companyData,
            OfficialEventAssessmentPreparationOperation eventAssessments) {
        this.companyData = companyData;
        this.eventAssessments = eventAssessments;
    }

    public Result prepare(int businessYear) {
        if (businessYear < 2000 || businessYear > 9999) {
            throw new IllegalArgumentException("Demo bootstrap business year is invalid");
        }

        var companies = companyData.prepareByStockCodes(REPRESENTATIVE_STOCK_CODES, businessYear);
        var preparedCompanyIds = companies.stream()
                .map(OfficialCompanyDataPreparationOperation.PreparedCompany::entityId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var assessments = eventAssessments.prepare().stream()
                .filter(prepared -> preparedCompanyIds.contains(prepared.companyId()))
                .toList();

        return new Result(
                companies.stream().map(company -> new PreparedCompany(
                        company.entityId(), company.stockCode(), company.companyName())).toList(),
                assessments.stream().map(OfficialEventAssessmentPreparationOperation.PreparedAssessment::eventId)
                        .distinct().count(),
                assessments.stream().map(OfficialEventAssessmentPreparationOperation.PreparedAssessment::assessmentId)
                        .distinct().count());
    }

    public record PreparedCompany(UUID entityId, String stockCode, String companyName) {}

    public record Result(List<PreparedCompany> companies, long eventCount, long assessmentCount) {
        public Result {
            companies = List.copyOf(companies);
        }

        public String summary() {
            String companySummary = companies.stream()
                    .map(company -> company.stockCode() + "=" + company.entityId())
                    .collect(java.util.stream.Collectors.joining(", "));
            return "Official demo bootstrap complete: companies=" + companies.size()
                    + ", events=" + eventCount + ", assessments=" + assessmentCount
                    + " [" + companySummary + "]";
        }
    }
}
