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

    private ProductOperation op(OperationType type, BigDecimal qty, LocalDateTime date) {
        return ProductOperation.builder()
                .operationId(UUID.randomUUID())
                .operationType(type)
                .quantity(qty)
                .operationDate(date)
                .build();
    }

    @Test
    @DisplayName("getInventoryComparison: считает inflow/outflow/delta + начальное состояние")
    void inventoryComparison_ShouldCalculateDelta() {
        when(inventoryRepository.findAll()).thenReturn(List.of(
                Inventory.builder().productId(UUID.randomUUID())
                        .quantity(new BigDecimal("100")).reservedQuantity(new BigDecimal("20")).build()
        ));
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        LocalDateTime mid = LocalDateTime.of(2026, 5, 15, 12, 0);
        when(operationRepository.findAll()).thenReturn(List.of(
                op(OperationType.RECEIPT, new BigDecimal("40"), mid),
                op(OperationType.WRITE_OFF, new BigDecimal("10"), mid),
                op(OperationType.RECEIPT, new BigDecimal("5"), LocalDateTime.of(2026, 4, 1, 0, 0))
        ));

        Map<String, Object> result = productAnalyticsService.getInventoryComparison(start, end);

        assertThat(result.get("inflow")).isEqualTo(40L);
        assertThat(result.get("outflow")).isEqualTo(10L);
        assertThat(result.get("delta")).isEqualTo(30L);
        assertThat(result.get("totalQuantityNow")).isEqualTo(100L);
        assertThat(result.get("totalQuantityAtStart")).isEqualTo(70L);
        assertThat(result.get("availableQuantityNow")).isEqualTo(80L);
        Double trend = (Double) result.get("totalQuantityTrendPercent");
        assertThat(trend).isCloseTo(42.857, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("getInventoryComparison: totalAtStart=0 → trendPercent=null (защита от деления)")
    void inventoryComparison_GivenZeroStart_ShouldReturnNullTrend() {
        when(inventoryRepository.findAll()).thenReturn(Collections.emptyList());
        LocalDate start = LocalDate.of(2026, 5, 1);
        LocalDate end = LocalDate.of(2026, 5, 31);
        when(operationRepository.findAll()).thenReturn(List.of(
                op(OperationType.RECEIPT, BigDecimal.TEN, LocalDateTime.of(2026, 5, 15, 12, 0))
        ));

        Map<String, Object> result = productAnalyticsService.getInventoryComparison(start, end);

        assertThat(result.get("totalQuantityTrendPercent")).isNull();
    }

    @Test
    @DisplayName("getOperationsComparison: считает previous period как [start-len, start-1] и deltaPercent")
    void operationsComparison_ShouldComputePreviousPeriod() {
        LocalDate start = LocalDate.of(2026, 5, 11);
        LocalDate end = LocalDate.of(2026, 5, 20);
        when(operationRepository.findAll()).thenReturn(List.of(
                op(OperationType.RECEIPT, BigDecimal.ONE, LocalDateTime.of(2026, 5, 15, 12, 0)),
                op(OperationType.RECEIPT, BigDecimal.ONE, LocalDateTime.of(2026, 5, 16, 12, 0)),
                op(OperationType.SHIPMENT, BigDecimal.ONE, LocalDateTime.of(2026, 5, 5, 12, 0))
        ));

        Map<String, Object> result = productAnalyticsService.getOperationsComparison(start, end);

        assertThat(result.get("currentTotal")).isEqualTo(2L);
        assertThat(result.get("previousTotal")).isEqualTo(1L);
        assertThat(result.get("previousStart")).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(result.get("previousEnd")).isEqualTo(LocalDate.of(2026, 5, 10));
        assertThat((Double) result.get("deltaPercent")).isEqualTo(100.0);
    }

    @Test
    @DisplayName("getOperationsComparison: previousTotal=0 → deltaPercent=null")
    void operationsComparison_GivenZeroPrevious_ShouldReturnNullDelta() {
        LocalDate start = LocalDate.of(2026, 5, 11);
        LocalDate end = LocalDate.of(2026, 5, 20);
        when(operationRepository.findAll()).thenReturn(List.of(
                op(OperationType.RECEIPT, BigDecimal.ONE, LocalDateTime.of(2026, 5, 15, 12, 0))
        ));

        Map<String, Object> result = productAnalyticsService.getOperationsComparison(start, end);

        assertThat(result.get("currentTotal")).isEqualTo(1L);
        assertThat(result.get("previousTotal")).isEqualTo(0L);
        assertThat(result.get("deltaPercent")).isNull();
    }

    @Test
    @DisplayName("getOperationsDynamics: операции с null operationDate отфильтровываются")
    void operationsDynamics_GivenNullDate_ShouldFilterOut() {
        ProductOperation nullDateOp = ProductOperation.builder()
                .operationId(UUID.randomUUID())
                .operationType(OperationType.RECEIPT)
                .quantity(BigDecimal.TEN)
                .operationDate(null)
                .build();
        when(operationRepository.findAll()).thenReturn(List.of(nullDateOp));

        Map<String, Object> result = productAnalyticsService.getOperationsDynamics(
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));

        assertThat(result.get("totalOperations")).isEqualTo(0);
    }
}

