package by.bsuir.warehouseservice.service;

import by.bsuir.warehouseservice.model.entity.WarehouseReadModel;
import by.bsuir.warehouseservice.repository.WarehouseReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseAnalyticsService Unit Tests")
class WarehouseAnalyticsServiceTest {

    @Mock
    private WarehouseReadModelRepository warehouseRepository;

    @InjectMocks
    private WarehouseAnalyticsService analyticsService;

    @Test
    @DisplayName("getWarehouseAnalytics: Given existing warehouse Should return analytics")
    void getWarehouseAnalytics_GivenExistingWarehouse_ShouldReturnAnalytics() {
        UUID warehouseId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID responsibleUserId = UUID.randomUUID();

        WarehouseReadModel warehouse = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .orgId(orgId)
                .name("Central Warehouse")
                .address("Main Street 1")
                .responsibleUserId(responsibleUserId)
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(warehouseRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));

        Map<String, Object> analytics = analyticsService.getWarehouseAnalytics(warehouseId);

        assertThat(analytics).isNotNull();
        assertThat(analytics.get("warehouseId")).isEqualTo(warehouseId);
        assertThat(analytics.get("name")).isEqualTo("Central Warehouse");
        assertThat(analytics.get("orgId")).isEqualTo(orgId);
        assertThat(analytics.get("isActive")).isEqualTo(true);
        assertThat(analytics).containsKey("structure");

        verify(warehouseRepository).findByWarehouseId(warehouseId);
    }

    @Test
    @DisplayName("getWarehouseAnalytics: Given non-existing warehouse Should throw exception")
    void getWarehouseAnalytics_GivenNonExistingWarehouse_ShouldThrowException() {
        UUID warehouseId = UUID.randomUUID();

        when(warehouseRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> analyticsService.getWarehouseAnalytics(warehouseId))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Warehouse not found");

        verify(warehouseRepository).findByWarehouseId(warehouseId);
    }

    @Test
    @DisplayName("getOrganizationWarehousesSummary: Should return summary with all warehouses")
    void getOrganizationWarehousesSummary_ShouldReturnSummaryWithAllWarehouses() {
        UUID orgId = UUID.randomUUID();

        WarehouseReadModel wh1 = WarehouseReadModel.builder()
                .warehouseId(UUID.randomUUID())
                .orgId(orgId)
                .name("Warehouse 1")
                .address("Address 1")
                .isActive(true)
                .build();

        WarehouseReadModel wh2 = WarehouseReadModel.builder()
                .warehouseId(UUID.randomUUID())
                .orgId(orgId)
                .name("Warehouse 2")
                .address("Address 2")
                .isActive(false)
                .build();

        when(warehouseRepository.findByOrgId(orgId)).thenReturn(List.of(wh1, wh2));

        Map<String, Object> summary = analyticsService.getOrganizationWarehousesSummary(orgId);

        assertThat(summary).isNotNull();
        assertThat(summary.get("orgId")).isEqualTo(orgId);
        assertThat(summary.get("totalWarehouses")).isEqualTo(2);
        assertThat(summary.get("activeWarehouses")).isEqualTo(1L);
        assertThat(summary).containsKey("warehouses");

        verify(warehouseRepository).findByOrgId(orgId);
    }

    @Test
    @DisplayName("getOrganizationWarehousesSummary: Given no warehouses Should return empty summary")
    void getOrganizationWarehousesSummary_GivenNoWarehouses_ShouldReturnEmptySummary() {
        UUID orgId = UUID.randomUUID();

        when(warehouseRepository.findByOrgId(orgId)).thenReturn(List.of());

        Map<String, Object> summary = analyticsService.getOrganizationWarehousesSummary(orgId);

        assertThat(summary).isNotNull();
        assertThat(summary.get("totalWarehouses")).isEqualTo(0);
        assertThat(summary.get("activeWarehouses")).isEqualTo(0L);

        verify(warehouseRepository).findByOrgId(orgId);
    }
}

