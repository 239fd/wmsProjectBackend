package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.response.InventoryResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.repository.InventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService Tests")
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private UUID inventoryId;
    private UUID productId;
    private UUID warehouseId;
    private UUID cellId;
    private UUID batchId;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        inventoryId = UUID.randomUUID();
        productId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        cellId = UUID.randomUUID();
        batchId = UUID.randomUUID();

        inventory = Inventory.builder()
                .inventoryId(inventoryId)
                .productId(productId)
                .batchId(batchId)
                .warehouseId(warehouseId)
                .cellId(cellId)
                .quantity(new BigDecimal("100"))
                .reservedQuantity(new BigDecimal("20"))
                .status(InventoryStatus.AVAILABLE)
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("Should get inventory by warehouse successfully")
    void shouldGetInventoryByWarehouseSuccessfully() {
        when(inventoryRepository.findByWarehouseId(warehouseId)).thenReturn(Arrays.asList(inventory));

        List<InventoryResponse> response = inventoryService.getInventoryByWarehouse(warehouseId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).warehouseId()).isEqualTo(warehouseId);
        assertThat(response.get(0).quantity()).isEqualTo(new BigDecimal("100"));
        verify(inventoryRepository, times(1)).findByWarehouseId(warehouseId);
    }

    @Test
    @DisplayName("Should get inventory by product successfully")
    void shouldGetInventoryByProductSuccessfully() {
        when(inventoryRepository.findByProductId(productId)).thenReturn(Arrays.asList(inventory));

        List<InventoryResponse> response = inventoryService.getInventoryByProduct(productId);

        assertThat(response).hasSize(1);
        assertThat(response.get(0).productId()).isEqualTo(productId);
        verify(inventoryRepository, times(1)).findByProductId(productId);
    }

    @Test
    @DisplayName("Should get inventory by cell successfully")
    void shouldGetInventoryByCellSuccessfully() {
        when(inventoryRepository.findByCellId(cellId)).thenReturn(Optional.of(inventory));

        InventoryResponse response = inventoryService.getInventoryByCell(cellId);

        assertThat(response).isNotNull();
        assertThat(response.cellId()).isEqualTo(cellId);
        verify(inventoryRepository, times(1)).findByCellId(cellId);
    }

    @Test
    @DisplayName("Should throw exception when inventory not found by cell")
    void shouldThrowExceptionWhenInventoryNotFoundByCell() {
        when(inventoryRepository.findByCellId(cellId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.getInventoryByCell(cellId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдены");

        verify(inventoryRepository, times(1)).findByCellId(cellId);
    }

    @Test
    @DisplayName("Should reserve inventory successfully")
    void shouldReserveInventorySuccessfully() {
        BigDecimal quantityToReserve = new BigDecimal("30");
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.reserve(productId, warehouseId, quantityToReserve);

        verify(inventoryRepository, times(1)).findByProductIdAndWarehouseId(productId, warehouseId);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
        assertThat(inventory.getReservedQuantity()).isEqualTo(new BigDecimal("50"));
    }

    @Test
    @DisplayName("Should throw exception when insufficient inventory for reservation")
    void shouldThrowExceptionWhenInsufficientInventoryForReservation() {
        BigDecimal quantityToReserve = new BigDecimal("100");
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.reserve(productId, warehouseId, quantityToReserve))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Недостаточно");

        verify(inventoryRepository, times(1)).findByProductIdAndWarehouseId(productId, warehouseId);
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when inventory not found for reservation")
    void shouldThrowExceptionWhenInventoryNotFoundForReservation() {
        BigDecimal quantityToReserve = new BigDecimal("10");
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserve(productId, warehouseId, quantityToReserve))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдены");

        verify(inventoryRepository, times(1)).findByProductIdAndWarehouseId(productId, warehouseId);
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should release reservation successfully")
    void shouldReleaseReservationSuccessfully() {
        BigDecimal quantityToRelease = new BigDecimal("10");
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.of(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        inventoryService.releaseReservation(productId, warehouseId, quantityToRelease);

        verify(inventoryRepository, times(1)).findByProductIdAndWarehouseId(productId, warehouseId);
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
        assertThat(inventory.getReservedQuantity()).isEqualTo(new BigDecimal("10"));
    }

    @Test
    @DisplayName("Should throw exception when releasing more than reserved")
    void shouldThrowExceptionWhenReleasingMoreThanReserved() {
        BigDecimal quantityToRelease = new BigDecimal("30");
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> inventoryService.releaseReservation(productId, warehouseId, quantityToRelease))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("больше");

        verify(inventoryRepository, times(1)).findByProductIdAndWarehouseId(productId, warehouseId);
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when inventory not found for release")
    void shouldThrowExceptionWhenInventoryNotFoundForRelease() {
        BigDecimal quantityToRelease = new BigDecimal("10");
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.releaseReservation(productId, warehouseId, quantityToRelease))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдены");

        verify(inventoryRepository, times(1)).findByProductIdAndWarehouseId(productId, warehouseId);
        verify(inventoryRepository, never()).save(any(Inventory.class));
    }
}

