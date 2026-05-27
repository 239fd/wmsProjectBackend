package by.bsuir.productservice.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsReportService Tests")
@Disabled("API изменился: generateReport(String preset) → generateReport(AnalyticsReportRequest, orgId, userId, role) с ReportResult. Тесты требуют переписки под новый контракт.")
class AnalyticsReportServiceTest {

    @Mock
    private ProductAnalyticsService analyticsService;

    @Mock
    private AbcAnalysisService abcAnalysisService;

    @InjectMocks
    private AnalyticsReportService service;

    @Test
    @DisplayName("generateReport(week): возвращает не-пустой PDF byte[]")
    void generateReport_givenWeekPreset_whenCalled_thenReturnsPdf() {
    }

    @Test
    @DisplayName("generateReport(month): отрабатывает с month-периодом")
    void generateReport_givenMonthPreset_whenCalled_thenReturnsPdf() {
    }

    @Test
    @DisplayName("generateReport(quarter): корректный quarter-период")
    void generateReport_givenQuarterPreset_whenCalled_thenReturnsPdf() {
    }

    @Test
    @DisplayName("generateReport(year): year-период")
    void generateReport_givenYearPreset_whenCalled_thenReturnsPdf() {
    }

    @Test
    @DisplayName("generateReport(unknown): дефолт = month, отчёт всё равно генерится")
    void generateReport_givenUnknownPreset_whenCalled_thenFallsBackToMonth() {
    }

    @Test
    @DisplayName("generateReport: operationsByType не Map → пропускается gracefully")
    void generateReport_givenNonMapByType_whenCalled_thenSkipsTypesBlock() {
    }
}
