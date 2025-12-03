package by.bsuir.productservice.service;

import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductAnalyticsService Tests")
class ProductAnalyticsServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductOperationRepository operationRepository;

    @InjectMocks
    private ProductAnalyticsService productAnalyticsService;

    private List<Inventory> inventoryList;
    private List<ProductOperation> operationList;

    @BeforeEach
    void setUp() {
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        Inventory inv1 = Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .productId(productId1)
                .quantity(new BigDecimal("100"))
                .reservedQuantity(new BigDecimal("20"))
                .build();

        Inventory inv2 = Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .productId(productId2)
                .quantity(new BigDecimal("50"))
                .reservedQuantity(new BigDecimal("10"))
                .build();

        inventoryList = Arrays.asList(inv1, inv2);

        ProductOperation op1 = ProductOperation.builder()
                .operationId(UUID.randomUUID())
                .operationType(OperationType.RECEIPT)
                .productId(productId1)
                .quantity(new BigDecimal("100"))
                .userId(userId)
                .operationDate(LocalDateTime.now().minusDays(1))
                .build();

        ProductOperation op2 = ProductOperation.builder()
                .operationId(UUID.randomUUID())
                .operationType(OperationType.SHIPMENT)
                .productId(productId1)
                .quantity(new BigDecimal("20"))
                .userId(userId)
                .operationDate(LocalDateTime.now())
                .build();

        operationList = Arrays.asList(op1, op2);
    }

    @Test
    @DisplayName("Should calculate inventory analytics successfully")
    void shouldCalculateInventoryAnalyticsSuccessfully() {
        when(inventoryRepository.findAll()).thenReturn(inventoryList);

        Map<String, Object> analytics = productAnalyticsService.getInventoryAnalytics();

        assertThat(analytics).isNotNull();
        assertThat(analytics.get("totalQuantity")).isEqualTo(150);
        assertThat(analytics.get("reservedQuantity")).isEqualTo(30);
        assertThat(analytics.get("availableQuantity")).isEqualTo(120);
        assertThat(analytics.get("uniqueProducts")).isEqualTo(2L);
        assertThat(analytics.get("totalRecords")).isEqualTo(2);
        verify(inventoryRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should calculate operations dynamics successfully")
    void shouldCalculateOperationsDynamicsSuccessfully() {
        LocalDate startDate = LocalDate.now().minusDays(2);
        LocalDate endDate = LocalDate.now();

        when(operationRepository.findAll()).thenReturn(operationList);

        Map<String, Object> dynamics = productAnalyticsService.getOperationsDynamics(startDate, endDate);

        assertThat(dynamics).isNotNull();
        assertThat(dynamics.get("startDate")).isEqualTo(startDate);
        assertThat(dynamics.get("endDate")).isEqualTo(endDate);
        assertThat(dynamics.get("totalOperations")).isEqualTo(2);

        @SuppressWarnings("unchecked")
        Map<String, Long> operationsByType = (Map<String, Long>) dynamics.get("operationsByType");
        assertThat(operationsByType).containsKey("RECEIPT");
        assertThat(operationsByType).containsKey("SHIPMENT");

        verify(operationRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should handle empty inventory in analytics")
    void shouldHandleEmptyInventoryInAnalytics() {
        when(inventoryRepository.findAll()).thenReturn(Collections.emptyList());

        Map<String, Object> analytics = productAnalyticsService.getInventoryAnalytics();

        assertThat(analytics).isNotNull();
        assertThat(analytics.get("totalQuantity")).isEqualTo(0);
        assertThat(analytics.get("reservedQuantity")).isEqualTo(0);
        assertThat(analytics.get("availableQuantity")).isEqualTo(0);
        assertThat(analytics.get("uniqueProducts")).isEqualTo(0L);
        verify(inventoryRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("Should handle empty operations in dynamics")
    void shouldHandleEmptyOperationsInDynamics() {
        LocalDate startDate = LocalDate.now().minusDays(2);
        LocalDate endDate = LocalDate.now();

        when(operationRepository.findAll()).thenReturn(Collections.emptyList());

        Map<String, Object> dynamics = productAnalyticsService.getOperationsDynamics(startDate, endDate);

        assertThat(dynamics).isNotNull();
        assertThat(dynamics.get("totalOperations")).isEqualTo(0);
        verify(operationRepository, times(1)).findAll();
    }
}

