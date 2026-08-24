package com.aira.api.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aira.api.analysis.service.OfficialEventAssessmentPreparationOperation;
import com.aira.api.market.opendart.OfficialCompanyDataPreparationOperation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OfficialDemoBootstrapOperationTests {
    private final OfficialCompanyDataPreparationOperation companyData =
            mock(OfficialCompanyDataPreparationOperation.class);
    private final OfficialEventAssessmentPreparationOperation eventAssessments =
            mock(OfficialEventAssessmentPreparationOperation.class);
    private final OfficialDemoBootstrapOperation bootstrap =
            new OfficialDemoBootstrapOperation(companyData, eventAssessments);

    @Test
    void preparesRepresentativeCompaniesBeforeTheirEventAssessments() {
        UUID firstCompany = UUID.randomUUID();
        UUID secondCompany = UUID.randomUUID();
        UUID firstEvent = UUID.randomUUID();
        UUID secondEvent = UUID.randomUUID();
        UUID firstAssessment = UUID.randomUUID();
        UUID secondAssessment = UUID.randomUUID();

        when(companyData.prepareByStockCodes(
                OfficialDemoBootstrapOperation.REPRESENTATIVE_STOCK_CODES, 2025)).thenReturn(List.of(
                        company(firstCompany, "000660", "SK hynix"),
                        company(secondCompany, "035420", "NAVER")));
        when(eventAssessments.prepare()).thenReturn(List.of(
                assessment(firstAssessment, firstEvent, firstCompany, "SK hynix"),
                assessment(secondAssessment, secondEvent, secondCompany, "NAVER"),
                assessment(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "Other")));

        var result = bootstrap.prepare(2025);

        assertEquals(2, result.companies().size());
        assertEquals(2, result.eventCount());
        assertEquals(2, result.assessmentCount());
        assertEquals("000660", result.companies().getFirst().stockCode());
        var order = inOrder(companyData, eventAssessments);
        order.verify(companyData).prepareByStockCodes(
                OfficialDemoBootstrapOperation.REPRESENTATIVE_STOCK_CODES, 2025);
        order.verify(eventAssessments).prepare();
    }

    @Test
    void rejectsInvalidBusinessYearBeforePreparation() {
        assertThrows(IllegalArgumentException.class, () -> bootstrap.prepare(1999));
    }

    private static OfficialCompanyDataPreparationOperation.PreparedCompany company(
            UUID entityId, String stockCode, String name) {
        return new OfficialCompanyDataPreparationOperation.PreparedCompany(
                entityId, "corp-code", stockCode, name, 12, 2025);
    }

    private static OfficialEventAssessmentPreparationOperation.PreparedAssessment assessment(
            UUID assessmentId, UUID eventId, UUID companyId, String name) {
        return new OfficialEventAssessmentPreparationOperation.PreparedAssessment(
                assessmentId, eventId, UUID.randomUUID(), companyId, name);
    }
}
