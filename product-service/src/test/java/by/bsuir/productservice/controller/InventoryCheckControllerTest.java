package by.bsuir.productservice.controller;

import by.bsuir.productservice.service.InventoryCheckService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryCheckController Unit Tests")
class InventoryCheckControllerTest {

    @Mock
    private InventoryCheckService inventoryCheckService;

    @InjectMocks
    private InventoryCheckController inventoryCheckController;

    @Test
    @DisplayName("startInventory: Given DIRECTOR role Should start and return 201")
    void startInventory_GivenDirectorRole_ShouldStartAndReturn201() {
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(inventoryCheckService.startInventory(eq(warehouseId), eq(userId), any(), any())).thenReturn(sessionId);

        ResponseEntity<Map<String, String>> response = inventoryCheckController.startInventory(
                warehouseId, userId, "Test notes", "DIRECTOR", UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("sessionId");
        verify(inventoryCheckService, times(1)).startInventory(eq(warehouseId), eq(userId), any(), any());
    }

    @Test
    @DisplayName("startInventory: Given ACCOUNTANT role Should start and return 201")
    void startInventory_GivenAccountantRole_ShouldStartAndReturn201() {
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        when(inventoryCheckService.startInventory(eq(warehouseId), eq(userId), any(), any())).thenReturn(sessionId);

        ResponseEntity<Map<String, String>> response = inventoryCheckController.startInventory(
                warehouseId, userId, "Test notes", "ACCOUNTANT", UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("sessionId");
        verify(inventoryCheckService, times(1)).startInventory(eq(warehouseId), eq(userId), any(), any());
    }

    @Test
    @DisplayName("startInventory: Given no role Should return 403")
    void startInventory_GivenNoRole_ShouldReturn403() {
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ResponseEntity<Map<String, String>> response = inventoryCheckController.startInventory(
                warehouseId, userId, "Test notes", null, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(inventoryCheckService, never()).startInventory(any(), any(), any(), any());
    }

    @Test
    @DisplayName("startInventory: Given wrong role Should return 403")
    void startInventory_GivenWrongRole_ShouldReturn403() {
        UUID warehouseId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ResponseEntity<Map<String, String>> response = inventoryCheckController.startInventory(
                warehouseId, userId, "Test notes", "ADMIN", UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(inventoryCheckService, never()).startInventory(any(), any(), any(), any());
    }

    @Test
    @DisplayName("recordActualCount: Given valid role Should record and return 200")
    void recordActualCount_GivenValidRole_ShouldRecordAndReturn200() {
        UUID sessionId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID cellId = UUID.randomUUID();
        BigDecimal actualQuantity = new BigDecimal("50.00");

        doNothing().when(inventoryCheckService).recordActualCount(any(), any(), any(), any(), any(), any());

        ResponseEntity<Map<String, String>> response = inventoryCheckController.recordActualCount(
                sessionId, null, productId, cellId, actualQuantity, "Test notes", "ACCOUNTANT", UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        verify(inventoryCheckService, times(1)).recordActualCount(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("recordActualCount: Given no role Should return 403")
    void recordActualCount_GivenNoRole_ShouldReturn403() {
        UUID sessionId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        BigDecimal actualQuantity = new BigDecimal("50.00");

        ResponseEntity<Map<String, String>> response = inventoryCheckController.recordActualCount(
                sessionId, null, productId, null, actualQuantity, "Test notes", null, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(inventoryCheckService, never()).recordActualCount(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("completeInventory: Given DIRECTOR role Should complete and return 200")
    void completeInventory_GivenDirectorRole_ShouldCompleteAndReturn200() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(inventoryCheckService.completeInventory(eq(sessionId), eq(userId), any()))
                .thenReturn(Map.of("message", "Completed"));

        ResponseEntity<Map<String, Object>> response = inventoryCheckController.completeInventory(
                sessionId, userId, "DIRECTOR", UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        verify(inventoryCheckService, times(1)).completeInventory(eq(sessionId), eq(userId), any());
    }

    @Test
    @DisplayName("completeInventory: Given ACCOUNTANT role Should complete and return 200")
    void completeInventory_GivenAccountantRole_ShouldCompleteAndReturn200() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        when(inventoryCheckService.completeInventory(eq(sessionId), eq(userId), any()))
                .thenReturn(Map.of("message", "Completed"));

        ResponseEntity<Map<String, Object>> response = inventoryCheckController.completeInventory(
                sessionId, userId, "ACCOUNTANT", UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).containsKey("message");
        verify(inventoryCheckService, times(1)).completeInventory(eq(sessionId), eq(userId), any());
    }

    @Test
    @DisplayName("completeInventory: Given wrong role Should return 403")
    void completeInventory_GivenWrongRole_ShouldReturn403() {
        UUID sessionId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        ResponseEntity<Map<String, Object>> response = inventoryCheckController.completeInventory(
                sessionId, userId, "ADMIN", UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(inventoryCheckService, never()).completeInventory(any(), any(), any());
    }
}

