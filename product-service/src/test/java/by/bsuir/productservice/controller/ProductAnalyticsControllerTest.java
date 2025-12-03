package by.bsuir.productservice.controller;

import by.bsuir.productservice.service.ProductAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductAnalyticsController Unit Tests")
class ProductAnalyticsControllerTest {

    @Mock
    private ProductAnalyticsService analyticsService;

    @InjectMocks
    private ProductAnalyticsController productAnalyticsController;

    @Test
    @DisplayName("getInventoryAnalytics: Given DIRECTOR role Should return analytics")
    void getInventoryAnalytics_GivenDirectorRole_ShouldReturnAnalytics() {
        Map<String, Object> analytics = Map.of("totalProducts", 100, "totalValue", 50000);

        when(analyticsService.getInventoryAnalytics()).thenReturn(analytics);

        ResponseEntity<Map<String, Object>> response = productAnalyticsController.getInventoryAnalytics("DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("totalProducts");
        verify(analyticsService, times(1)).getInventoryAnalytics();
    }

    @Test
    @DisplayName("getInventoryAnalytics: Given no role Should return 403")
    void getInventoryAnalytics_GivenNoRole_ShouldReturn403() {
        ResponseEntity<Map<String, Object>> response = productAnalyticsController.getInventoryAnalytics(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(analyticsService, never()).getInventoryAnalytics();
    }

    @Test
    @DisplayName("getInventoryAnalytics: Given non-DIRECTOR role Should return 403")
    void getInventoryAnalytics_GivenNonDirectorRole_ShouldReturn403() {
        ResponseEntity<Map<String, Object>> response = productAnalyticsController.getInventoryAnalytics("ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(analyticsService, never()).getInventoryAnalytics();
    }

    @Test
    @DisplayName("getOperationsDynamics: Given DIRECTOR role Should return dynamics")
    void getOperationsDynamics_GivenDirectorRole_ShouldReturnDynamics() {
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();
        Map<String, Object> dynamics = Map.of("operations", 150, "totalValue", 30000);

        when(analyticsService.getOperationsDynamics(any(LocalDate.class), any(LocalDate.class))).thenReturn(dynamics);

        ResponseEntity<Map<String, Object>> response = productAnalyticsController.getOperationsDynamics(
                startDate, endDate, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("operations");
        verify(analyticsService, times(1)).getOperationsDynamics(any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("getOperationsDynamics: Given non-DIRECTOR role Should return 403")
    void getOperationsDynamics_GivenNonDirectorRole_ShouldReturn403() {
        LocalDate startDate = LocalDate.now().minusDays(30);
        LocalDate endDate = LocalDate.now();

        ResponseEntity<Map<String, Object>> response = productAnalyticsController.getOperationsDynamics(
                startDate, endDate, "ADMIN");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(analyticsService, never()).getOperationsDynamics(any(), any());
    }

    @Test
    @DisplayName("getOperationsSummary: Given DIRECTOR role Should return summary")
    void getOperationsSummary_GivenDirectorRole_ShouldReturnSummary() {
        Map<String, Object> summary = Map.of("operations", 150, "totalValue", 30000);

        when(analyticsService.getOperationsDynamics(any(LocalDate.class), any(LocalDate.class))).thenReturn(summary);

        ResponseEntity<Map<String, Object>> response = productAnalyticsController.getOperationsSummary("DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("operations");
        verify(analyticsService, times(1)).getOperationsDynamics(any(LocalDate.class), any(LocalDate.class));
    }

    @Test
    @DisplayName("getOperationsSummary: Given non-DIRECTOR role Should return 403")
    void getOperationsSummary_GivenNonDirectorRole_ShouldReturn403() {
        ResponseEntity<Map<String, Object>> response = productAnalyticsController.getOperationsSummary("ACCOUNTANT");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(analyticsService, never()).getOperationsDynamics(any(), any());
    }
}

