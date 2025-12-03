package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.response.InventoryResponse;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.service.InventoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryController Unit Tests")
class InventoryControllerTest {

    @Mock
    private InventoryService inventoryService;

    @InjectMocks
    private InventoryController inventoryController;

    @Test
    @DisplayName("getInventoryByWarehouse: Should return inventory list")
    void getInventoryByWarehouse_ShouldReturnInventoryList() {
        UUID warehouseId = UUID.randomUUID();
        InventoryResponse inventory = new InventoryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                warehouseId,
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("90.00"),
                InventoryStatus.AVAILABLE,
                LocalDateTime.now()
        );

        when(inventoryService.getInventoryByWarehouse(warehouseId)).thenReturn(List.of(inventory));

        ResponseEntity<List<InventoryResponse>> response = inventoryController.getInventoryByWarehouse(warehouseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).warehouseId()).isEqualTo(warehouseId);
        verify(inventoryService, times(1)).getInventoryByWarehouse(warehouseId);
    }

    @Test
    @DisplayName("getInventoryByProduct: Should return inventory list")
    void getInventoryByProduct_ShouldReturnInventoryList() {
        UUID productId = UUID.randomUUID();
        InventoryResponse inventory = new InventoryResponse(
                UUID.randomUUID(),
                productId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("90.00"),
                InventoryStatus.AVAILABLE,
                LocalDateTime.now()
        );

        when(inventoryService.getInventoryByProduct(productId)).thenReturn(List.of(inventory));

        ResponseEntity<List<InventoryResponse>> response = inventoryController.getInventoryByProduct(productId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        assertThat(response.getBody().get(0).productId()).isEqualTo(productId);
        verify(inventoryService, times(1)).getInventoryByProduct(productId);
    }

    @Test
    @DisplayName("getInventoryByCell: Should return inventory")
    void getInventoryByCell_ShouldReturnInventory() {
        UUID cellId = UUID.randomUUID();
        InventoryResponse inventory = new InventoryResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                cellId,
                new BigDecimal("100.00"),
                new BigDecimal("10.00"),
                new BigDecimal("90.00"),
                InventoryStatus.AVAILABLE,
                LocalDateTime.now()
        );

        when(inventoryService.getInventoryByCell(cellId)).thenReturn(inventory);

        ResponseEntity<InventoryResponse> response = inventoryController.getInventoryByCell(cellId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().cellId()).isEqualTo(cellId);
        verify(inventoryService, times(1)).getInventoryByCell(cellId);
    }
}

