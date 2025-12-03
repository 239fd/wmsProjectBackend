package by.bsuir.warehouseservice.controller;

import by.bsuir.warehouseservice.dto.request.CreateWarehouseRequest;
import by.bsuir.warehouseservice.dto.request.UpdateWarehouseRequest;
import by.bsuir.warehouseservice.dto.response.WarehouseResponse;
import by.bsuir.warehouseservice.service.WarehouseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseController Unit Tests")
class WarehouseControllerTest {

    @Mock
    private WarehouseService warehouseService;

    @InjectMocks
    private WarehouseController warehouseController;

    @Test
    @DisplayName("createWarehouse: Should create and return 201")
    void createWarehouse_ShouldCreateAndReturn201() {
        CreateWarehouseRequest request = new CreateWarehouseRequest(
                UUID.randomUUID(),
                "Test Warehouse",
                "Test Address",
                UUID.randomUUID()
        );

        WarehouseResponse expectedResponse = new WarehouseResponse(
                UUID.randomUUID(),
                request.orgId(),
                "Test Warehouse",
                "Test Address",
                request.responsibleUserId(),
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(warehouseService.createWarehouse(any(CreateWarehouseRequest.class)))
                .thenReturn(expectedResponse);

        ResponseEntity<WarehouseResponse> response = warehouseController.createWarehouse(request, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(warehouseService).createWarehouse(request);
    }

    @Test
    @DisplayName("getWarehouse: Should return warehouse")
    void getWarehouse_ShouldReturnWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        WarehouseResponse expectedResponse = new WarehouseResponse(
                warehouseId,
                UUID.randomUUID(),
                "Test Warehouse",
                "Test Address",
                UUID.randomUUID(),
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(warehouseService.getWarehouse(warehouseId)).thenReturn(expectedResponse);

        ResponseEntity<WarehouseResponse> response = warehouseController.getWarehouse(warehouseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(warehouseService).getWarehouse(warehouseId);
    }

    @Test
    @DisplayName("getWarehousesByOrganization: Should return list of warehouses")
    void getWarehousesByOrganization_ShouldReturnListOfWarehouses() {
        UUID orgId = UUID.randomUUID();
        List<WarehouseResponse> expectedList = List.of(
                new WarehouseResponse(UUID.randomUUID(), orgId, "WH1", "Addr1", null, true, LocalDateTime.now(), LocalDateTime.now())
        );

        when(warehouseService.getWarehousesByOrganization(orgId)).thenReturn(expectedList);

        ResponseEntity<List<WarehouseResponse>> response = warehouseController.getWarehousesByOrganization(orgId, false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(warehouseService).getWarehousesByOrganization(orgId);
    }

    @Test
    @DisplayName("updateWarehouse: Should update and return warehouse")
    void updateWarehouse_ShouldUpdateAndReturnWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        UpdateWarehouseRequest request = new UpdateWarehouseRequest(
                "Updated Name",
                "Updated Address",
                null,
                true
        );

        WarehouseResponse expectedResponse = new WarehouseResponse(
                warehouseId,
                UUID.randomUUID(),
                "Updated Name",
                "Updated Address",
                null,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(warehouseService.updateWarehouse(eq(warehouseId), any(UpdateWarehouseRequest.class)))
                .thenReturn(expectedResponse);

        ResponseEntity<WarehouseResponse> response = warehouseController.updateWarehouse(warehouseId, request, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(warehouseService).updateWarehouse(warehouseId, request);
    }

    @Test
    @DisplayName("activateWarehouse: Should activate and return warehouse")
    void activateWarehouse_ShouldActivateAndReturnWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        WarehouseResponse expectedResponse = new WarehouseResponse(
                warehouseId,
                UUID.randomUUID(),
                "Test Warehouse",
                "Test Address",
                null,
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(warehouseService.activateWarehouse(warehouseId)).thenReturn(expectedResponse);

        ResponseEntity<WarehouseResponse> response = warehouseController.activateWarehouse(warehouseId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isTrue();
        verify(warehouseService).activateWarehouse(warehouseId);
    }

    @Test
    @DisplayName("deactivateWarehouse: Should deactivate and return warehouse")
    void deactivateWarehouse_ShouldDeactivateAndReturnWarehouse() {
        UUID warehouseId = UUID.randomUUID();
        WarehouseResponse expectedResponse = new WarehouseResponse(
                warehouseId,
                UUID.randomUUID(),
                "Test Warehouse",
                "Test Address",
                null,
                false,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(warehouseService.deactivateWarehouse(warehouseId)).thenReturn(expectedResponse);

        ResponseEntity<WarehouseResponse> response = warehouseController.deactivateWarehouse(warehouseId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().isActive()).isFalse();
        verify(warehouseService).deactivateWarehouse(warehouseId);
    }

    @Test
    @DisplayName("deleteWarehouse: Should delete and return 200")
    void deleteWarehouse_ShouldDeleteAndReturn200() {
        UUID warehouseId = UUID.randomUUID();

        doNothing().when(warehouseService).deleteWarehouse(warehouseId);

        ResponseEntity<Map<String, String>> response = warehouseController.deleteWarehouse(warehouseId, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        verify(warehouseService).deleteWarehouse(warehouseId);
    }

    @Test
    @DisplayName("getWarehouseInfo: Should return warehouse info map")
    void getWarehouseInfo_ShouldReturnWarehouseInfoMap() {
        UUID warehouseId = UUID.randomUUID();
        Map<String, Object> expectedInfo = Map.of(
                "warehouseId", warehouseId,
                "name", "Test Warehouse"
        );

        when(warehouseService.getWarehouseInfo(warehouseId)).thenReturn(expectedInfo);

        ResponseEntity<Map<String, Object>> response = warehouseController.getWarehouseInfo(warehouseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedInfo);
        verify(warehouseService).getWarehouseInfo(warehouseId);
    }
}

