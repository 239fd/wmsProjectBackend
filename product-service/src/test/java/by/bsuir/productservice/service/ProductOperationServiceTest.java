package by.bsuir.productservice.service;

import by.bsuir.productservice.client.WarehouseClient;
import by.bsuir.productservice.client.dto.CellInfoDto;
import by.bsuir.productservice.client.dto.RackInfoDto;
import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.TransferProductRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductOperationService — модульные тесты")
class ProductOperationServiceTest {

    @Mock private ProductOperationRepository operationRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductReadModelRepository productRepository;
    @Mock private WarehouseClient warehouseClient;
    @Mock private InventoryEventService inventoryEventService;

    @InjectMocks private ProductOperationService service;

    @Test
    @DisplayName("receiveProduct: новый inventory → создаётся запись с quantity и операция RECEIPT")
    void receiveProduct_GivenNewInventory_ShouldCreateInventoryAndOperation() {
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        ReceiveProductRequest req = new ReceiveProductRequest(
                productId, null, warehouseId, cellId, BigDecimal.valueOf(10), userId, null, null);

        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).organizationId(orgId).build()));
        when(inventoryRepository.findByCellId(cellId)).thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, warehouseId))
                .thenReturn(Optional.empty());

        UUID operationId = service.receiveProduct(req, orgId);

        assertThat(operationId).isNotNull();
        ArgumentCaptor<ProductOperation> opCaptor = ArgumentCaptor.forClass(ProductOperation.class);
        verify(operationRepository).save(opCaptor.capture());
        assertThat(opCaptor.getValue().getOperationType()).isEqualTo(OperationType.RECEIPT);
        assertThat(opCaptor.getValue().getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(opCaptor.getValue().getOrganizationId()).isEqualTo(orgId);

        ArgumentCaptor<Inventory> invCaptor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository).save(invCaptor.capture());
        assertThat(invCaptor.getValue().getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(10));
        assertThat(invCaptor.getValue().getReservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invCaptor.getValue().getOrganizationId()).isEqualTo(orgId);
    }

    @Test
    @DisplayName("receiveProduct: существующий inventory → quantity складывается, ячейка обновляется")
    void receiveProduct_GivenExistingInventory_ShouldAccumulateQuantity() {
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        Inventory existing = Inventory.builder()
                .inventoryId(UUID.randomUUID()).productId(productId)
                .warehouseId(warehouseId).organizationId(orgId)
                .quantity(BigDecimal.valueOf(5)).reservedQuantity(BigDecimal.ZERO)
                .build();
        ReceiveProductRequest req = new ReceiveProductRequest(
                productId, null, warehouseId, cellId, BigDecimal.valueOf(7), userId, null, null);

        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).organizationId(orgId).build()));
        when(inventoryRepository.findByCellId(cellId)).thenReturn(Optional.empty());
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, warehouseId))
                .thenReturn(Optional.of(existing));

        service.receiveProduct(req, orgId);

        assertThat(existing.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(12));
        assertThat(existing.getCellId()).isEqualTo(cellId);
    }

    @Test
    @DisplayName("receiveProduct: целевая ячейка занята → 409 conflict")
    void receiveProduct_GivenOccupiedCell_ShouldThrowConflict() {
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        Inventory existing = Inventory.builder()
                .inventoryId(UUID.randomUUID()).cellId(cellId)
                .quantity(BigDecimal.valueOf(5)).reservedQuantity(BigDecimal.ZERO)
                .build();
        ReceiveProductRequest req = new ReceiveProductRequest(
                productId, null, warehouseId, cellId, BigDecimal.valueOf(1), userId, null, null);

        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).build()));
        when(inventoryRepository.findByCellId(cellId)).thenReturn(Optional.of(existing));

        AppException ex = catchApp(() -> service.receiveProduct(req, null));
        assertThat(ex.getStatus().value()).isEqualTo(409);
        assertThat(ex.getMessage()).contains("занята");
        verify(operationRepository, never()).save(any());
    }

    @Test
    @DisplayName("receiveProduct: товар не найден → 404")
    void receiveProduct_GivenMissingProduct_ShouldThrowNotFound() {
        UUID productId = UUID.randomUUID();
        ReceiveProductRequest req = new ReceiveProductRequest(
                productId, null, UUID.randomUUID(), null, BigDecimal.ONE, UUID.randomUUID(), null, null);
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        AppException ex = catchApp(() -> service.receiveProduct(req));
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("receiveProduct: количество = 0 → 400 bad request")
    void receiveProduct_GivenZeroQuantity_ShouldThrowBadRequest() {
        ReceiveProductRequest req = new ReceiveProductRequest(
                UUID.randomUUID(), null, UUID.randomUUID(), null,
                BigDecimal.ZERO, UUID.randomUUID(), null, null);

        AppException ex = catchApp(() -> service.receiveProduct(req));
        assertThat(ex.getStatus().value()).isEqualTo(400);
    }

    @Test
    @DisplayName("receiveProduct: cellId не указан, есть свободные ячейки → проходит")
    void receiveProduct_GivenNoCellWithFreeCellsAvailable_ShouldSucceed() {
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        UUID freeCellId = UUID.randomUUID();
        ReceiveProductRequest req = new ReceiveProductRequest(
                productId, null, warehouseId, null, BigDecimal.ONE, UUID.randomUUID(), null, null);

        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).build()));
        when(warehouseClient.getRacksByWarehouse(eq(warehouseId), anyString())).thenReturn(List.of(
                new RackInfoDto(rackId, warehouseId, "CELL", "A-1", null, true)));
        when(inventoryRepository.findByWarehouseId(warehouseId)).thenReturn(List.of());
        when(warehouseClient.getCellsByRack(eq(rackId), anyString())).thenReturn(List.of(
                new CellInfoDto(freeCellId, rackId, BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE)));
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, warehouseId))
                .thenReturn(Optional.empty());

        service.receiveProduct(req);

        verify(operationRepository).save(any(ProductOperation.class));
    }

    @Test
    @DisplayName("transferProduct: между складами → списывает source, создаёт dest, операция TRANSFER")
    void transferProduct_GivenValid_ShouldDecrementSourceAndCreateDest() {
        UUID productId = UUID.randomUUID();
        UUID fromWh = UUID.randomUUID();
        UUID toWh = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Inventory source = Inventory.builder()
                .inventoryId(UUID.randomUUID()).productId(productId)
                .warehouseId(fromWh).organizationId(orgId)
                .quantity(BigDecimal.valueOf(20)).reservedQuantity(BigDecimal.valueOf(5))
                .build();

        TransferProductRequest req = new TransferProductRequest(
                productId, fromWh, toWh, null, null, null, BigDecimal.valueOf(10), userId, null);

        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).organizationId(orgId).build()));
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, fromWh))
                .thenReturn(Optional.of(source));
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, toWh))
                .thenReturn(Optional.empty());

        service.transferProduct(req, orgId);

        assertThat(source.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(10));
        ArgumentCaptor<Inventory> invCaptor = ArgumentCaptor.forClass(Inventory.class);
        verify(inventoryRepository, times(2)).save(invCaptor.capture());
        Inventory dest = invCaptor.getAllValues().get(1);
        assertThat(dest.getWarehouseId()).isEqualTo(toWh);
        assertThat(dest.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(10));

        ArgumentCaptor<ProductOperation> opCaptor = ArgumentCaptor.forClass(ProductOperation.class);
        verify(operationRepository).save(opCaptor.capture());
        assertThat(opCaptor.getValue().getOperationType()).isEqualTo(OperationType.TRANSFER);
    }

    @Test
    @DisplayName("transferProduct: тенантная защита — source принадлежит другой орг → 403")
    void transferProduct_GivenOtherTenantSource_ShouldThrowForbidden() {
        UUID productId = UUID.randomUUID();
        UUID fromWh = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();
        UUID myOrg = UUID.randomUUID();
        Inventory source = Inventory.builder()
                .productId(productId).warehouseId(fromWh)
                .organizationId(otherOrg)
                .quantity(BigDecimal.valueOf(20)).reservedQuantity(BigDecimal.ZERO)
                .build();

        TransferProductRequest req = new TransferProductRequest(
                productId, fromWh, UUID.randomUUID(), null, null, null,
                BigDecimal.valueOf(5), UUID.randomUUID(), null);

        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).organizationId(myOrg).build()));
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, fromWh))
                .thenReturn(Optional.of(source));

        AppException ex = catchApp(() -> service.transferProduct(req, myOrg));
        assertThat(ex.getStatus().value()).isEqualTo(403);
    }

    @Test
    @DisplayName("transferProduct: не хватает доступного количества (учитывая reserved) → 400")
    void transferProduct_GivenInsufficientAvailable_ShouldThrowBadRequest() {
        UUID productId = UUID.randomUUID();
        UUID fromWh = UUID.randomUUID();
        Inventory source = Inventory.builder()
                .productId(productId).warehouseId(fromWh)
                .quantity(BigDecimal.valueOf(10)).reservedQuantity(BigDecimal.valueOf(8))
                .build();

        TransferProductRequest req = new TransferProductRequest(
                productId, fromWh, UUID.randomUUID(), null, null, null,
                BigDecimal.valueOf(5), UUID.randomUUID(), null);

        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).build()));
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, fromWh))
                .thenReturn(Optional.of(source));

        AppException ex = catchApp(() -> service.transferProduct(req, null));
        assertThat(ex.getStatus().value()).isEqualTo(400);
        assertThat(ex.getMessage()).contains("Доступно: 2");
    }

    @Test
    @DisplayName("transferProduct: source-инвентарь не найден → 404")
    void transferProduct_GivenMissingSource_ShouldThrowNotFound() {
        UUID productId = UUID.randomUUID();
        UUID fromWh = UUID.randomUUID();
        TransferProductRequest req = new TransferProductRequest(
                productId, fromWh, UUID.randomUUID(), null, null, null,
                BigDecimal.ONE, UUID.randomUUID(), null);

        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).build()));
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, fromWh))
                .thenReturn(Optional.empty());

        AppException ex = catchApp(() -> service.transferProduct(req, null));
        assertThat(ex.getStatus().value()).isEqualTo(404);
    }

    @Test
    @DisplayName("transferProduct: существующий dest по ячейке → quantity складывается")
    void transferProduct_GivenExistingDestByCell_ShouldAccumulate() {
        UUID productId = UUID.randomUUID();
        UUID fromWh = UUID.randomUUID();
        UUID toWh = UUID.randomUUID();
        UUID toCell = UUID.randomUUID();
        Inventory source = Inventory.builder()
                .productId(productId).warehouseId(fromWh)
                .quantity(BigDecimal.valueOf(20)).reservedQuantity(BigDecimal.ZERO)
                .build();
        Inventory destExisting = Inventory.builder()
                .productId(productId).warehouseId(toWh).cellId(toCell)
                .quantity(BigDecimal.valueOf(3)).reservedQuantity(BigDecimal.ZERO)
                .build();
        TransferProductRequest req = new TransferProductRequest(
                productId, fromWh, toWh, null, toCell, null,
                BigDecimal.valueOf(5), UUID.randomUUID(), null);

        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).build()));
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, fromWh))
                .thenReturn(Optional.of(source));
        when(inventoryRepository.findByCellId(toCell)).thenReturn(Optional.of(destExisting));

        service.transferProduct(req, null);

        assertThat(destExisting.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(8));
    }

    private static AppException catchApp(Runnable r) {
        try {
            r.run();
        } catch (AppException e) {
            return e;
        }
        throw new AssertionError("Expected AppException");
    }
}
