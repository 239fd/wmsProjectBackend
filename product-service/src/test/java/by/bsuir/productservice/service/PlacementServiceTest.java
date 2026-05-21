package by.bsuir.productservice.service;

import by.bsuir.productservice.client.WarehouseClient;
import by.bsuir.productservice.client.dto.CellInfoDto;
import by.bsuir.productservice.client.dto.RackInfoDto;
import by.bsuir.productservice.dto.request.PlacementRequest;
import by.bsuir.productservice.dto.response.PlacementResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.StorageConditions;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PlacementService — модульные тесты")
class PlacementServiceTest {

    @Mock private WarehouseClient warehouseClient;
    @Mock private ProductBatchRepository batchRepository;
    @Mock private ProductReadModelRepository productRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductOperationRepository operationRepository;

    @InjectMocks private PlacementService service;

    private ProductBatch batch(UUID id, UUID orgId, UUID productId, StorageConditions sc) {
        return ProductBatch.builder()
                .batchId(id).organizationId(orgId).productId(productId)
                .storageConditions(sc).build();
    }

    private RackInfoDto rack(UUID id, String name, String sc, boolean active) {
        return new RackInfoDto(id, UUID.randomUUID(), "SHELF", name, sc, active);
    }

    private CellInfoDto cell(UUID id, UUID rackId) {
        return new CellInfoDto(id, rackId, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);
    }

    @Test
    @DisplayName("autoPlacement: батча нет → notFound")
    void autoPlacement_GivenMissingBatch_ShouldThrow() {
        UUID batchId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.empty());

        PlacementRequest req = new PlacementRequest(batchId, UUID.randomUUID(),
                BigDecimal.ONE, UUID.randomUUID(), null, null);
        assertThatThrownBy(() -> service.autoPlacement(req, null, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Партия не найдена");
    }

    @Test
    @DisplayName("autoPlacement: чужая организация → forbidden")
    void autoPlacement_GivenForeignOrg_ShouldThrow() {
        UUID batchId = UUID.randomUUID();
        UUID batchOrg = UUID.randomUUID();
        UUID callerOrg = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(
                batch(batchId, batchOrg, UUID.randomUUID(), StorageConditions.ROOM)));

        PlacementRequest req = new PlacementRequest(batchId, UUID.randomUUID(),
                BigDecimal.ONE, UUID.randomUUID(), null, null);

        assertThatThrownBy(() -> service.autoPlacement(req, callerOrg, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("другой организации");
    }

    @Test
    @DisplayName("autoPlacement: продукт удалён → notFound")
    void autoPlacement_GivenMissingProduct_ShouldThrow() {
        UUID batchId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(
                batch(batchId, null, productId, StorageConditions.ROOM)));
        when(productRepository.findById(productId)).thenReturn(Optional.empty());

        PlacementRequest req = new PlacementRequest(batchId, UUID.randomUUID(),
                BigDecimal.ONE, UUID.randomUUID(), null, null);

        assertThatThrownBy(() -> service.autoPlacement(req, null, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Товар не найден");
    }

    @Test
    @DisplayName("autoPlacement: нет стеллажей с подходящими условиями → conflict")
    void autoPlacement_GivenNoMatchingRacks_ShouldThrow() {
        UUID batchId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(
                batch(batchId, null, productId, StorageConditions.FRIDGE)));
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).abcClass("A").build()));
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER"))
                .thenReturn(List.of(rack(UUID.randomUUID(), "R1", "ROOM", true)));

        PlacementRequest req = new PlacementRequest(batchId, warehouseId,
                BigDecimal.ONE, UUID.randomUUID(), null, null);

        assertThatThrownBy(() -> service.autoPlacement(req, null, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("FRIDGE");
    }

    @Test
    @DisplayName("autoPlacement: все ячейки заняты → conflict")
    void autoPlacement_GivenAllCellsOccupied_ShouldThrow() {
        UUID batchId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(
                batch(batchId, null, productId, StorageConditions.ROOM)));
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).abcClass("B").build()));
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER"))
                .thenReturn(List.of(rack(rackId, "R1", "ROOM", true)));
        when(warehouseClient.getCellsByRack(rackId, "WORKER"))
                .thenReturn(List.of(cell(cellId, rackId)));
        when(inventoryRepository.findByWarehouseId(warehouseId))
                .thenReturn(List.of(Inventory.builder()
                        .cellId(cellId).quantity(BigDecimal.TEN).build()));

        PlacementRequest req = new PlacementRequest(batchId, warehouseId,
                BigDecimal.ONE, UUID.randomUUID(), null, null);

        assertThatThrownBy(() -> service.autoPlacement(req, null, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("недостаточно места");
    }

    @Test
    @DisplayName("autoPlacement: happy path → создаёт Inventory + ProductOperation STAGING, mode=AUTO")
    void autoPlacement_GivenFreeCells_ShouldPersistInventoryAndOperation() {
        UUID batchId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(
                batch(batchId, UUID.randomUUID(), productId, StorageConditions.ROOM)));
        when(productRepository.findById(productId)).thenReturn(Optional.of(
                ProductReadModel.builder().productId(productId).abcClass("A").build()));
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER"))
                .thenReturn(List.of(rack(rackId, "R1", "ROOM", true)));
        when(warehouseClient.getCellsByRack(rackId, "WORKER"))
                .thenReturn(List.of(cell(cellId, rackId)));
        when(inventoryRepository.findByWarehouseId(warehouseId)).thenReturn(List.of());

        PlacementRequest req = new PlacementRequest(batchId, warehouseId,
                new BigDecimal("5"), UUID.randomUUID(), null, "приёмка");
        PlacementResponse resp = service.autoPlacement(req, null, "WORKER");

        assertThat(resp.cellId()).isEqualTo(cellId);
        assertThat(resp.rackId()).isEqualTo(rackId);
        assertThat(resp.mode()).isEqualTo("AUTO");
        verify(inventoryRepository).save(org.mockito.ArgumentMatchers.any(Inventory.class));
        verify(operationRepository).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("manualPlacement: без cellId → bad request")
    void manualPlacement_GivenMissingCell_ShouldThrow() {
        PlacementRequest req = new PlacementRequest(UUID.randomUUID(), UUID.randomUUID(),
                BigDecimal.ONE, UUID.randomUUID(), null, null);

        assertThatThrownBy(() -> service.manualPlacement(req, null, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Cell ID");
    }

    @Test
    @DisplayName("manualPlacement: ячейка не принадлежит складу → notFound")
    void manualPlacement_GivenCellNotInWarehouse_ShouldThrow() {
        UUID batchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(
                batch(batchId, null, UUID.randomUUID(), StorageConditions.ROOM)));
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER")).thenReturn(List.of());

        PlacementRequest req = new PlacementRequest(batchId, warehouseId,
                BigDecimal.ONE, UUID.randomUUID(), cellId, null);

        assertThatThrownBy(() -> service.manualPlacement(req, null, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найдена");
    }

    @Test
    @DisplayName("manualPlacement: условия ячейки не совпадают → conflict")
    void manualPlacement_GivenWrongConditions_ShouldThrow() {
        UUID batchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(
                batch(batchId, null, UUID.randomUUID(), StorageConditions.FRIDGE)));
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER"))
                .thenReturn(List.of(rack(rackId, "R1", "ROOM", true)));
        when(warehouseClient.getCellsByRack(rackId, "WORKER"))
                .thenReturn(List.of(cell(cellId, rackId)));

        PlacementRequest req = new PlacementRequest(batchId, warehouseId,
                BigDecimal.ONE, UUID.randomUUID(), cellId, null);

        assertThatThrownBy(() -> service.manualPlacement(req, null, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Условия хранения");
    }

    @Test
    @DisplayName("manualPlacement: ячейка занята → conflict")
    void manualPlacement_GivenOccupiedCell_ShouldThrow() {
        UUID batchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(
                batch(batchId, null, UUID.randomUUID(), StorageConditions.ROOM)));
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER"))
                .thenReturn(List.of(rack(rackId, "R1", "ROOM", true)));
        when(warehouseClient.getCellsByRack(rackId, "WORKER"))
                .thenReturn(List.of(cell(cellId, rackId)));
        when(inventoryRepository.findByCellId(cellId)).thenReturn(Optional.of(
                Inventory.builder().cellId(cellId).quantity(BigDecimal.TEN).build()));

        PlacementRequest req = new PlacementRequest(batchId, warehouseId,
                BigDecimal.ONE, UUID.randomUUID(), cellId, null);

        assertThatThrownBy(() -> service.manualPlacement(req, null, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("занята");
    }

    @Test
    @DisplayName("manualPlacement: happy path → создаёт Inventory + Operation MANUAL")
    void manualPlacement_GivenValidCell_ShouldPersist() {
        UUID batchId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(
                batch(batchId, UUID.randomUUID(), UUID.randomUUID(), StorageConditions.ROOM)));
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER"))
                .thenReturn(List.of(rack(rackId, "R1", "ROOM", true)));
        when(warehouseClient.getCellsByRack(rackId, "WORKER"))
                .thenReturn(List.of(cell(cellId, rackId)));
        when(inventoryRepository.findByCellId(cellId)).thenReturn(Optional.empty());

        PlacementRequest req = new PlacementRequest(batchId, warehouseId,
                new BigDecimal("3"), UUID.randomUUID(), cellId, null);
        PlacementResponse resp = service.manualPlacement(req, null, "WORKER");

        assertThat(resp.cellId()).isEqualTo(cellId);
        assertThat(resp.mode()).isEqualTo("MANUAL");
        verify(inventoryRepository).save(org.mockito.ArgumentMatchers.any());
        verify(operationRepository).save(org.mockito.ArgumentMatchers.any());
    }
}
