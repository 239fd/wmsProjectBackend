package by.bsuir.productservice.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AnalyticsReportService Tests")
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
        when(analyticsService.getOperationsDynamics(any(), any()))
                .thenReturn(Map.of(
                        "totalOperations", 42,
                        "operationsByType", Map.of("RECEIPT", 10, "SHIPMENT", 30)));
        when(analyticsService.getInventoryAnalytics())
                .thenReturn(Map.of(
                        "uniqueProducts", 5,
                        "totalQuantity", 100,
                        "reservedQuantity", 20,
                        "availableQuantity", 80));
        when(abcAnalysisService.getAbcReport()).thenReturn(List.of(
                Map.of("productId", "p1", "abcClass", "A")));

        byte[] pdf = service.generateReport("week");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("generateReport(month): отрабатывает с month-периодом")
    void generateReport_givenMonthPreset_whenCalled_thenReturnsPdf() {
        when(analyticsService.getOperationsDynamics(any(), any())).thenReturn(Map.of());
        when(analyticsService.getInventoryAnalytics()).thenReturn(Map.of());
        when(abcAnalysisService.getAbcReport()).thenReturn(List.of());

        byte[] pdf = service.generateReport("month");

        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("generateReport(quarter): корректный quarter-период")
    void generateReport_givenQuarterPreset_whenCalled_thenReturnsPdf() {
        when(analyticsService.getOperationsDynamics(any(), any())).thenReturn(Map.of());
        when(analyticsService.getInventoryAnalytics()).thenReturn(Map.of());
        when(abcAnalysisService.getAbcReport()).thenReturn(List.of());

        byte[] pdf = service.generateReport("quarter");

        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("generateReport(year): year-период")
    void generateReport_givenYearPreset_whenCalled_thenReturnsPdf() {
        when(analyticsService.getOperationsDynamics(any(), any())).thenReturn(Map.of());
        when(analyticsService.getInventoryAnalytics()).thenReturn(Map.of());
        when(abcAnalysisService.getAbcReport()).thenReturn(List.of());

        byte[] pdf = service.generateReport("year");

        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("generateReport(unknown): дефолт = month, отчёт всё равно генерится")
    void generateReport_givenUnknownPreset_whenCalled_thenFallsBackToMonth() {
        when(analyticsService.getOperationsDynamics(any(), any())).thenReturn(Map.of());
        when(analyticsService.getInventoryAnalytics()).thenReturn(Map.of());
        when(abcAnalysisService.getAbcReport()).thenReturn(List.of());

        byte[] pdf = service.generateReport("totally-unknown");

        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("generateReport: operationsByType не Map → пропускается gracefully")
    void generateReport_givenNonMapByType_whenCalled_thenSkipsTypesBlock() {
        when(analyticsService.getOperationsDynamics(any(), any()))
                .thenReturn(Map.of("totalOperations", 1, "operationsByType", "not-a-map"));
        when(analyticsService.getInventoryAnalytics()).thenReturn(Map.of());
        when(abcAnalysisService.getAbcReport()).thenReturn(List.of());

        byte[] pdf = service.generateReport("month");

        assertThat(pdf).isNotEmpty();
    }
}
