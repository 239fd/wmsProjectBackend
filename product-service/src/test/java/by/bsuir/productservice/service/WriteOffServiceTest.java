package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.WriteOffRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryCount;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryCountRepository;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WriteOffService — модульные тесты")
class WriteOffServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductOperationRepository operationRepository;
    @Mock private InventoryCountRepository countRepository;
    @Mock private InventoryEventService inventoryEventService;

    @InjectMocks private WriteOffService service;

    @Test
    @DisplayName("writeOff: достаточно товара → списывает, создаёт операцию WRITE_OFF, заполняет notes")
    void writeOff_GivenEnough_ShouldDecrementAndCreateOperation() {
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Inventory inv = Inventory.builder()
                .inventoryId(UUID.randomUUID()).productId(productId)
                .warehouseId(warehouseId).organizationId(orgId)
                .quantity(BigDecimal.valueOf(10)).reservedQuantity(BigDecimal.valueOf(2))
                .build();
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, warehouseId))
                .thenReturn(Optional.of(inv));

        WriteOffRequest req = new WriteOffRequest(
                productId, warehouseId, null, null, BigDecimal.valueOf(5),
                "DAMAGE", "Приказ #42", UUID.randomUUID(),
                List.of(UUID.randomUUID(), UUID.randomUUID()),
                userId, "повреждено при хранении");

        Map<String, Object> result = service.writeOff(req, orgId);

        assertThat(inv.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(result).containsKey("operationId");
        assertThat(result).containsEntry("reason", "DAMAGE");

        ArgumentCaptor<ProductOperation> opCaptor = ArgumentCaptor.forClass(ProductOperation.class);
        verify(operationRepository).save(opCaptor.capture());
        ProductOperation op = opCaptor.getValue();
        assertThat(op.getOperationType()).isEqualTo(OperationType.WRITE_OFF);
        assertThat(op.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(5));
        assertThat(op.getOrganizationId()).isEqualTo(orgId);
        assertThat(op.getNotes()).contains("reason=DAMAGE");
        assertThat(op.getNotes()).contains("basis=Приказ #42");
        assertThat(op.getNotes()).contains("commission=");
        assertThat(op.getNotes()).contains("повреждено при хранении");
    }

    @Test
    @DisplayName("writeOff: запас не найден → 404")
    void writeOff_GivenMissingInventory_ShouldThrowNotFound() {
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, warehouseId))
                .thenReturn(Optional.empty());

        WriteOffRequest req = new WriteOffRequest(productId, warehouseId, null, null,
                BigDecimal.ONE, "DAMAGE", null, null, null, UUID.randomUUID(), null);

        AppException ex = catchApp(() -> service.writeOff(req, null));
        assertThat(ex.getStatus().value()).isEqualTo(404);
        verify(operationRepository, never()).save(any());
    }

    @Test
    @DisplayName("writeOff: запас другой организации → 403 forbidden")
    void writeOff_GivenForeignTenantInventory_ShouldThrowForbidden() {
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID myOrg = UUID.randomUUID();
        UUID otherOrg = UUID.randomUUID();
        Inventory inv = Inventory.builder()
                .inventoryId(UUID.randomUUID()).productId(productId)
                .warehouseId(warehouseId).organizationId(otherOrg)
                .quantity(BigDecimal.TEN).reservedQuantity(BigDecimal.ZERO)
                .build();
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, warehouseId))
                .thenReturn(Optional.of(inv));

        WriteOffRequest req = new WriteOffRequest(productId, warehouseId, null, null,
                BigDecimal.ONE, "DAMAGE", null, null, null, UUID.randomUUID(), null);

        AppException ex = catchApp(() -> service.writeOff(req, myOrg));
        assertThat(ex.getStatus().value()).isEqualTo(403);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("writeOff: недостаточно (учитывая reserved) → 400 с указанием доступного и запрошенного")
    void writeOff_GivenInsufficient_ShouldThrowBadRequest() {
        UUID productId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        Inventory inv = Inventory.builder()
                .inventoryId(UUID.randomUUID()).productId(productId)
                .warehouseId(warehouseId)
                .quantity(BigDecimal.valueOf(10)).reservedQuantity(BigDecimal.valueOf(8))
                .build();
        when(inventoryRepository.findByProductIdAndWarehouseIdForUpdate(productId, warehouseId))
                .thenReturn(Optional.of(inv));

        WriteOffRequest req = new WriteOffRequest(productId, warehouseId, null, null,
                BigDecimal.valueOf(5), "DAMAGE", null, null, null, UUID.randomUUID(), null);

        AppException ex = catchApp(() -> service.writeOff(req, null));
        assertThat(ex.getStatus().value()).isEqualTo(400);
        assertThat(ex.getMessage()).contains("Доступно: 2");
        assertThat(ex.getMessage()).contains("запрошено: 5");
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("getMarkedItems: orgId == null → пустой список (защита от leakage)")
    void getMarkedItems_GivenNoOrg_ShouldReturnEmpty() {
        List<Map<String, Object>> result = service.getMarkedItems(UUID.randomUUID(), null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getMarkedItems: warehouseId указан → фильтр по orgId+warehouseId, маппит поля")
    void getMarkedItems_GivenWarehouseId_ShouldFilterAndMap() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        InventoryCount c = InventoryCount.builder()
                .countId(UUID.randomUUID()).sessionId(UUID.randomUUID())
                .productId(UUID.randomUUID()).cellId(UUID.randomUUID())
                .warehouseId(warehouseId).organizationId(orgId)
                .expectedQuantity(BigDecimal.valueOf(10)).actualQuantity(BigDecimal.valueOf(7))
                .discrepancy(BigDecimal.valueOf(-3)).markedForWriteoff(true)
                .notes("battery dead").build();
        when(countRepository.findByOrganizationIdAndWarehouseIdAndMarkedForWriteoffTrue(orgId, warehouseId))
                .thenReturn(List.of(c));

        List<Map<String, Object>> result = service.getMarkedItems(warehouseId, orgId);

        assertThat(result).hasSize(1);
        assertThat(result.get(0)).containsEntry("countId", c.getCountId());
        assertThat(result.get(0)).containsEntry("sessionId", c.getSessionId());
        assertThat(result.get(0)).containsEntry("expectedQuantity", BigDecimal.valueOf(10));
        assertThat(result.get(0)).containsEntry("actualQuantity", BigDecimal.valueOf(7));
        assertThat(result.get(0)).containsEntry("discrepancy", BigDecimal.valueOf(-3));
        assertThat(result.get(0)).containsEntry("notes", "battery dead");
    }

    @Test
    @DisplayName("getMarkedItems: warehouseId == null → фильтр по orgId, без warehouse")
    void getMarkedItems_GivenNoWarehouseId_ShouldFilterByOrgOnly() {
        UUID orgId = UUID.randomUUID();
        when(countRepository.findByOrganizationIdAndMarkedForWriteoffTrue(orgId))
                .thenReturn(List.of());

        List<Map<String, Object>> result = service.getMarkedItems(null, orgId);

        assertThat(result).isEmpty();
        verify(countRepository).findByOrganizationIdAndMarkedForWriteoffTrue(orgId);
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
