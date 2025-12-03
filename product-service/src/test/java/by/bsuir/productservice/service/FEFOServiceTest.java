package by.bsuir.productservice.service;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FEFOService Tests")
class FEFOServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductBatchRepository batchRepository;

    @InjectMocks
    private FEFOService fefoService;

    private UUID productId;
    private UUID warehouseId;
    private UUID batchId;
    private Inventory inventory;
    private ProductBatch batch;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        batchId = UUID.randomUUID();

        inventory = Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .productId(productId)
                .batchId(batchId)
                .warehouseId(warehouseId)
                .cellId(UUID.randomUUID())
                .quantity(new BigDecimal("100"))
                .reservedQuantity(new BigDecimal("20"))
                .status(InventoryStatus.AVAILABLE)
                .lastUpdated(LocalDateTime.now())
                .build();

        batch = ProductBatch.builder()
                .batchId(batchId)
                .productId(productId)
                .batchNumber("BATCH-001")
                .manufactureDate(LocalDate.now().minusMonths(3))
                .expiryDate(LocalDate.now().plusMonths(3))
                .supplier("Test Supplier")
                .purchasePrice(new BigDecimal("100.00"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should select inventory by FEFO successfully")
    void shouldSelectInventoryByFefoSuccessfully() {
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.of(inventory));
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        var allocations = fefoService.selectInventoryByFEFO(
                productId, warehouseId, new BigDecimal("50")
        );

        assertThat(allocations).isNotEmpty();
        assertThat(allocations.get(0).getQuantity()).isEqualTo(new BigDecimal("50"));
        verify(inventoryRepository, times(1)).findByProductIdAndWarehouseId(productId, warehouseId);
    }

    @Test
    @DisplayName("Should throw exception when product not found in warehouse")
    void shouldThrowExceptionWhenProductNotFoundInWarehouse() {
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> fefoService.selectInventoryByFEFO(
                productId, warehouseId, new BigDecimal("50")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("отсутствует");

        verify(inventoryRepository, times(1)).findByProductIdAndWarehouseId(productId, warehouseId);
    }

    @Test
    @DisplayName("Should throw exception when insufficient inventory for FEFO")
    void shouldThrowExceptionWhenInsufficientInventoryForFefo() {
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.of(inventory));
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        assertThatThrownBy(() -> fefoService.selectInventoryByFEFO(
                productId, warehouseId, new BigDecimal("100")))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Недостаточно");

        verify(inventoryRepository, times(1)).findByProductIdAndWarehouseId(productId, warehouseId);
    }

    @Test
    @DisplayName("Should handle inventory without batch information")
    void shouldHandleInventoryWithoutBatchInformation() {
        inventory.setBatchId(null);

        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.of(inventory));

        var allocations = fefoService.selectInventoryByFEFO(
                productId, warehouseId, new BigDecimal("50")
        );

        assertThat(allocations).isNotEmpty();
        assertThat(allocations.get(0).getQuantity()).isEqualTo(new BigDecimal("50"));
        verify(batchRepository, never()).findById(any());
    }
}

