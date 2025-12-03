package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.ReserveProductRequest;
import by.bsuir.productservice.dto.request.ShipProductRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductOperationService Tests")
class ProductOperationServiceTest {

    @Mock
    private ProductOperationRepository operationRepository;

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private ProductReadModelRepository productRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private FEFOService fefoService;

    @InjectMocks
    private ProductOperationService productOperationService;

    private UUID productId;
    private UUID warehouseId;
    private UUID cellId;
    private UUID batchId;
    private UUID userId;
    private ProductReadModel product;
    private Inventory inventory;

    @BeforeEach
    void setUp() {
        productId = UUID.randomUUID();
        warehouseId = UUID.randomUUID();
        cellId = UUID.randomUUID();
        batchId = UUID.randomUUID();
        userId = UUID.randomUUID();

        product = ProductReadModel.builder()
                .productId(productId)
                .name("Test Product")
                .sku("SKU-001")
                .build();

        inventory = Inventory.builder()
                .inventoryId(UUID.randomUUID())
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
    @DisplayName("Should receive product and create new inventory successfully")
    void shouldReceiveProductAndCreateNewInventorySuccessfully() {
        ReceiveProductRequest request = new ReceiveProductRequest(
                productId, batchId, warehouseId, cellId, new BigDecimal("50"), userId, "Test notes"
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.empty());
        when(operationRepository.save(any(ProductOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID operationId = productOperationService.receiveProduct(request);

        assertThat(operationId).isNotNull();
        verify(productRepository, times(1)).findById(productId);
        verify(operationRepository, times(1)).save(any(ProductOperation.class));
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should receive product and update existing inventory successfully")
    void shouldReceiveProductAndUpdateExistingInventorySuccessfully() {
        ReceiveProductRequest request = new ReceiveProductRequest(
                productId, batchId, warehouseId, cellId, new BigDecimal("50"), userId, "Test notes"
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.of(inventory));
        when(operationRepository.save(any(ProductOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID operationId = productOperationService.receiveProduct(request);

        assertThat(operationId).isNotNull();
        assertThat(inventory.getQuantity()).isEqualTo(new BigDecimal("150"));
        verify(productRepository, times(1)).findById(productId);
        verify(operationRepository, times(1)).save(any(ProductOperation.class));
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when receiving product with null productId")
    void shouldThrowExceptionWhenReceivingProductWithNullProductId() {
        ReceiveProductRequest request = new ReceiveProductRequest(
                null, batchId, warehouseId, cellId, new BigDecimal("50"), userId, "Test notes"
        );

        assertThatThrownBy(() -> productOperationService.receiveProduct(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Product ID");

        verify(productRepository, never()).findById(any());
    }

    @Test
    @DisplayName("Should throw exception when receiving product with null warehouseId")
    void shouldThrowExceptionWhenReceivingProductWithNullWarehouseId() {
        ReceiveProductRequest request = new ReceiveProductRequest(
                productId, batchId, null, cellId, new BigDecimal("50"), userId, "Test notes"
        );

        assertThatThrownBy(() -> productOperationService.receiveProduct(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Warehouse ID");
    }

    @Test
    @DisplayName("Should throw exception when receiving product with invalid quantity")
    void shouldThrowExceptionWhenReceivingProductWithInvalidQuantity() {
        ReceiveProductRequest request = new ReceiveProductRequest(
                productId, batchId, warehouseId, cellId, BigDecimal.ZERO, userId, "Test notes"
        );

        assertThatThrownBy(() -> productOperationService.receiveProduct(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("больше 0");
    }

    @Test
    @DisplayName("Should throw exception when product not found during receive")
    void shouldThrowExceptionWhenProductNotFoundDuringReceive() {
        ReceiveProductRequest request = new ReceiveProductRequest(
                productId, batchId, warehouseId, cellId, new BigDecimal("50"), userId, "Test notes"
        );

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productOperationService.receiveProduct(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");

        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    @DisplayName("Should ship product successfully")
    void shouldShipProductSuccessfully() {
        ShipProductRequest request = new ShipProductRequest(
                productId, batchId, warehouseId, cellId, new BigDecimal("30"), userId, "Test notes"
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.of(inventory));
        when(operationRepository.save(any(ProductOperation.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(inventoryRepository.save(any(Inventory.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UUID operationId = productOperationService.shipProduct(request);

        assertThat(operationId).isNotNull();
        assertThat(inventory.getQuantity()).isEqualTo(new BigDecimal("70"));
        verify(productRepository, times(1)).findById(productId);
        verify(operationRepository, times(1)).save(any(ProductOperation.class));
        verify(inventoryRepository, times(1)).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when shipping with insufficient inventory")
    void shouldThrowExceptionWhenShippingWithInsufficientInventory() {
        ShipProductRequest request = new ShipProductRequest(
                productId, batchId, warehouseId, cellId, new BigDecimal("100"), userId, "Test notes"
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.of(inventory));

        assertThatThrownBy(() -> productOperationService.shipProduct(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Недостаточно");

        verify(inventoryRepository, never()).save(any(Inventory.class));
    }

    @Test
    @DisplayName("Should throw exception when shipping product not found")
    void shouldThrowExceptionWhenShippingProductNotFound() {
        ShipProductRequest request = new ShipProductRequest(
                productId, batchId, warehouseId, cellId, new BigDecimal("30"), userId, "Test notes"
        );

        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productOperationService.shipProduct(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");

        verify(productRepository, times(1)).findById(productId);
    }

    @Test
    @DisplayName("Should throw exception when shipping from non-existent inventory")
    void shouldThrowExceptionWhenShippingFromNonExistentInventory() {
        ShipProductRequest request = new ShipProductRequest(
                productId, batchId, warehouseId, cellId, new BigDecimal("30"), userId, "Test notes"
        );

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(inventoryRepository.findByProductIdAndWarehouseId(productId, warehouseId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> productOperationService.shipProduct(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдены");

        verify(inventoryRepository, times(1)).findByProductIdAndWarehouseId(productId, warehouseId);
    }

    @Test
    @DisplayName("Should reserve product successfully")
    void shouldReserveProductSuccessfully() {
        ReserveProductRequest request = new ReserveProductRequest(
                productId, batchId, warehouseId, new BigDecimal("30"), "Test notes"
        );

        doNothing().when(inventoryService).reserve(productId, warehouseId, new BigDecimal("30"));

        productOperationService.reserveProduct(request);

        verify(inventoryService, times(1)).reserve(productId, warehouseId, new BigDecimal("30"));
    }

    @Test
    @DisplayName("Should release reservation successfully")
    void shouldReleaseReservationSuccessfully() {
        BigDecimal quantity = new BigDecimal("10");

        doNothing().when(inventoryService).releaseReservation(productId, warehouseId, quantity);

        productOperationService.releaseReservation(productId, warehouseId, quantity);

        verify(inventoryService, times(1)).releaseReservation(productId, warehouseId, quantity);
    }
}

