package by.bsuir.warehouseservice.controller;

import by.bsuir.warehouseservice.service.WarehouseAnalyticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseAnalyticsController Unit Tests")
class WarehouseAnalyticsControllerTest {

    @Mock
    private WarehouseAnalyticsService analyticsService;

    @InjectMocks
    private WarehouseAnalyticsController analyticsController;

    @Test
    @DisplayName("getWarehouseAnalytics: Should return analytics")
    void getWarehouseAnalytics_ShouldReturnAnalytics() {
        UUID warehouseId = UUID.randomUUID();
        Map<String, Object> expectedAnalytics = Map.of(
                "warehouseId", warehouseId,
                "name", "Test Warehouse",
                "totalRacks", 10
        );

        when(analyticsService.getWarehouseAnalytics(warehouseId)).thenReturn(expectedAnalytics);

        ResponseEntity<Map<String, Object>> response = analyticsController.getWarehouseAnalytics(warehouseId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedAnalytics);
        verify(analyticsService).getWarehouseAnalytics(warehouseId);
    }

    @Test
    @DisplayName("getOrganizationWarehousesSummary: Should return summary")
    void getOrganizationWarehousesSummary_ShouldReturnSummary() {
        UUID orgId = UUID.randomUUID();
        Map<String, Object> expectedSummary = Map.of(
                "orgId", orgId,
                "totalWarehouses", 5,
                "activeWarehouses", 3L
        );

        when(analyticsService.getOrganizationWarehousesSummary(orgId)).thenReturn(expectedSummary);

        ResponseEntity<Map<String, Object>> response = analyticsController.getOrganizationWarehousesSummary(orgId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedSummary);
        verify(analyticsService).getOrganizationWarehousesSummary(orgId);
    }
}

