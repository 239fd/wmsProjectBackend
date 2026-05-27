package by.bsuir.productservice.service;

import by.bsuir.productservice.client.WarehouseClient;
import by.bsuir.productservice.client.dto.CellInfoDto;
import by.bsuir.productservice.client.dto.RackInfoDto;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.repository.InventoryRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BarcodeService — модульные тесты")
class BarcodeServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private WarehouseClient warehouseClient;

    @InjectMocks private BarcodeService service;

    private Inventory inventory(UUID id, UUID orgId, UUID warehouseId, UUID cellId) {
        return Inventory.builder()
                .inventoryId(id).organizationId(orgId)
                .warehouseId(warehouseId).cellId(cellId)
                .quantity(BigDecimal.TEN).build();
    }

    @Test
    @DisplayName("assignSku: inventory не найден → notFound")
    void assignSku_GivenMissingInventory_ShouldThrow() {
        UUID id = UUID.randomUUID();
        when(inventoryRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignSkuToInventory(id, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Запас не найден");
    }

    @Test
    @DisplayName("assignSku: inventory без cellId → bad request")
    void assignSku_GivenNoCellId_ShouldThrow() {
        UUID id = UUID.randomUUID();
        when(inventoryRepository.findById(id)).thenReturn(Optional.of(
                inventory(id, UUID.randomUUID(), UUID.randomUUID(), null)));

        assertThatThrownBy(() -> service.assignSkuToInventory(id, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("ячейка");
    }

    @Test
    @DisplayName("assignSku: inventory без organizationId → bad request")
    void assignSku_GivenNoOrgId_ShouldThrow() {
        UUID id = UUID.randomUUID();
        when(inventoryRepository.findById(id)).thenReturn(Optional.of(
                inventory(id, null, UUID.randomUUID(), UUID.randomUUID())));

        assertThatThrownBy(() -> service.assignSkuToInventory(id, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("organizationId");
    }

    @Test
    @DisplayName("assignSku: rack для ячейки не найден → notFound")
    void assignSku_GivenCellWithoutRack_ShouldThrow() {
        UUID id = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        when(inventoryRepository.findById(id)).thenReturn(Optional.of(
                inventory(id, UUID.randomUUID(), warehouseId, cellId)));
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER"))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.assignSkuToInventory(id, "WORKER"))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Стеллаж");
    }

    @Test
    @DisplayName("assignSku: happy path → формирует SKU и сохраняет в Inventory")
    void assignSku_GivenValid_ShouldGenerateAndPersist() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        Inventory inv = inventory(id, orgId, warehouseId, cellId);
        when(inventoryRepository.findById(id)).thenReturn(Optional.of(inv));
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER")).thenReturn(List.of(
                new RackInfoDto(rackId, warehouseId, "SHELF", "R1", "ROOM", null, true)));
        when(warehouseClient.getCellsByRack(rackId, "WORKER")).thenReturn(List.of(
                new CellInfoDto(cellId, rackId, null, null, BigDecimal.TEN, BigDecimal.TEN,
                        BigDecimal.TEN, BigDecimal.TEN, null, null, null)));
        when(warehouseClient.getRack(rackId, "WORKER")).thenReturn(
                new RackInfoDto(rackId, warehouseId, "SHELF", "R1", "ROOM", null, true));
        when(inventoryRepository.findByOrganizationIdAndUnitSku(any(), anyString())).thenReturn(Optional.empty());

        String sku = service.assignSkuToInventory(id, "WORKER");

        assertThat(sku).isNotBlank();
        assertThat(sku.length()).isEqualTo(16);
        assertThat(sku.charAt(12)).isEqualTo('S');
        assertThat(inv.getUnitSku()).isEqualTo(sku);
        verify(inventoryRepository).save(inv);
    }

    @Test
    @DisplayName("formatSku: PALLET → код 'P', cell-номер в последних 3 символах")
    void formatSku_GivenPalletKind_ShouldUsePCode() {
        UUID orgId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID rackId = UUID.randomUUID();
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER")).thenReturn(List.of(
                new RackInfoDto(rackId, warehouseId, "PALLET", "P1", null, null, true)));

        String sku = service.formatSku(orgId, warehouseId, rackId, "PALLET", 5);

        assertThat(sku).hasSize(16);
        assertThat(sku.charAt(12)).isEqualTo('P');
        assertThat(sku.substring(13)).isEqualTo("005");
    }

    @Test
    @DisplayName("formatSku: неизвестный rackKind → код 'X'")
    void formatSku_GivenUnknownKind_ShouldUseXCode() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER")).thenReturn(List.of());

        String sku = service.formatSku(UUID.randomUUID(), warehouseId, UUID.randomUUID(), "UNKNOWN", 1);

        assertThat(sku.charAt(12)).isEqualTo('X');
    }

    @Test
    @DisplayName("formatSku: rackKind=null → код 'X'")
    void formatSku_GivenNullKind_ShouldUseXCode() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseClient.getRacksByWarehouse(warehouseId, "WORKER")).thenReturn(List.of());

        String sku = service.formatSku(UUID.randomUUID(), warehouseId, UUID.randomUUID(), null, 1);

        assertThat(sku.charAt(12)).isEqualTo('X');
    }

    @Test
    @DisplayName("generateBarcodeSheetPdf: пустой список → bad request")
    void generateBarcodeSheet_GivenEmpty_ShouldThrow() {
        assertThatThrownBy(() -> service.generateBarcodeSheetPdf(List.of(), null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("пуст");
    }

    @Test
    @DisplayName("generateBarcodeSheetPdf: null список → bad request")
    void generateBarcodeSheet_GivenNull_ShouldThrow() {
        assertThatThrownBy(() -> service.generateBarcodeSheetPdf(null, null))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("пуст");
    }

    @Test
    @DisplayName("generateBarcodeSheetPdf: inventory без unitSku → пропускается, PDF не пустой")
    void generateBarcodeSheet_GivenInventoryWithoutSku_ShouldSkip() {
        UUID id = UUID.randomUUID();
        Inventory inv = inventory(id, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
        inv.setUnitSku(null);
        lenient().when(inventoryRepository.findById(id)).thenReturn(Optional.of(inv));

        byte[] pdf = service.generateBarcodeSheetPdf(List.of(id), null);
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("generateBarcodeSheetPdf: inventory чужой организации → пропускается")
    void generateBarcodeSheet_GivenForeignOrg_ShouldSkip() {
        UUID id = UUID.randomUUID();
        UUID inventoryOrg = UUID.randomUUID();
        UUID callerOrg = UUID.randomUUID();
        Inventory inv = inventory(id, inventoryOrg, UUID.randomUUID(), UUID.randomUUID());
        inv.setUnitSku("AAAABBBBCCCCS001");
        lenient().when(inventoryRepository.findById(id)).thenReturn(Optional.of(inv));

        byte[] pdf = service.generateBarcodeSheetPdf(List.of(id), callerOrg);
        assertThat(pdf).isNotEmpty();
    }

    @Test
    @DisplayName("generateBarcodeSheetPdf: успешный кейс → валидный PDF")
    void generateBarcodeSheet_GivenValid_ShouldReturnPdf() {
        UUID id = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        Inventory inv = inventory(id, orgId, UUID.randomUUID(), UUID.randomUUID());
        inv.setUnitSku("AAAABBBBCCCCS001");
        when(inventoryRepository.findById(id)).thenReturn(Optional.of(inv));

        byte[] pdf = service.generateBarcodeSheetPdf(List.of(id), orgId);
        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }
}
