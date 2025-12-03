package by.bsuir.warehouseservice.controller;

import by.bsuir.warehouseservice.dto.request.*;
import by.bsuir.warehouseservice.dto.response.RackResponse;
import by.bsuir.warehouseservice.model.enums.RackKind;
import by.bsuir.warehouseservice.service.RackService;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RackController Unit Tests")
class RackControllerTest {

    @Mock
    private RackService rackService;

    @InjectMocks
    private RackController rackController;

    @Test
    @DisplayName("createRack: Should create and return 201")
    void createRack_ShouldCreateAndReturn201() {
        CreateRackRequest request = new CreateRackRequest(
                UUID.randomUUID(),
                RackKind.SHELF,
                "Rack A1"
        );

        RackResponse expectedResponse = new RackResponse(
                UUID.randomUUID(),
                request.warehouseId(),
                RackKind.SHELF,
                "Rack A1",
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(rackService.createRack(any(CreateRackRequest.class))).thenReturn(expectedResponse);

        ResponseEntity<RackResponse> response = rackController.createRack(request, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(rackService).createRack(request);
    }

    @Test
    @DisplayName("createRack: Given no role Should return 403")
    void createRack_GivenNoRole_ShouldReturn403() {
        CreateRackRequest request = new CreateRackRequest(
                UUID.randomUUID(),
                RackKind.SHELF,
                "Rack A1"
        );

        ResponseEntity<RackResponse> response = rackController.createRack(request, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        verify(rackService, never()).createRack(any());
    }

    @Test
    @DisplayName("createShelf: Should create shelf and return 201")
    void createShelf_ShouldCreateShelfAndReturn201() {
        UUID rackId = UUID.randomUUID();
        CreateShelfRequest request = new CreateShelfRequest(
                rackId,
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(200.0),
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(50.0)
        );

        doNothing().when(rackService).createShelf(any(CreateShelfRequest.class));

        ResponseEntity<Map<String, String>> response = rackController.createShelf(rackId, request, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("message");
        verify(rackService).createShelf(any(CreateShelfRequest.class));
    }

    @Test
    @DisplayName("createCell: Should create cell and return 201")
    void createCell_ShouldCreateCellAndReturn201() {
        UUID rackId = UUID.randomUUID();
        CreateCellRequest request = new CreateCellRequest(
                rackId,
                BigDecimal.valueOf(50.0),
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(100.0)
        );

        doNothing().when(rackService).createCell(any(CreateCellRequest.class));

        ResponseEntity<Map<String, String>> response = rackController.createCell(rackId, request, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("message");
        verify(rackService).createCell(any(CreateCellRequest.class));
    }

    @Test
    @DisplayName("createFridge: Should create fridge and return 201")
    void createFridge_ShouldCreateFridgeAndReturn201() {
        UUID rackId = UUID.randomUUID();
        CreateFridgeRequest request = new CreateFridgeRequest(
                rackId,
                BigDecimal.valueOf(-18.0),
                BigDecimal.valueOf(200.0),
                BigDecimal.valueOf(150.0),
                BigDecimal.valueOf(200.0)
        );

        doNothing().when(rackService).createFridge(any(CreateFridgeRequest.class));

        ResponseEntity<Map<String, String>> response = rackController.createFridge(rackId, request, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("message");
        verify(rackService).createFridge(any(CreateFridgeRequest.class));
    }

    @Test
    @DisplayName("createPallet: Should create pallet and return 201")
    void createPallet_ShouldCreatePalletAndReturn201() {
        UUID rackId = UUID.randomUUID();
        CreatePalletRequest request = new CreatePalletRequest(
                rackId,
                10,
                BigDecimal.valueOf(1000.0)
        );

        doNothing().when(rackService).createPallet(any(CreatePalletRequest.class));

        ResponseEntity<Map<String, String>> response = rackController.createPallet(rackId, request, "DIRECTOR");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).containsKey("message");
        verify(rackService).createPallet(any(CreatePalletRequest.class));
    }

    @Test
    @DisplayName("getRacksByWarehouse: Should return list of racks")
    void getRacksByWarehouse_ShouldReturnListOfRacks() {
        UUID warehouseId = UUID.randomUUID();
        List<RackResponse> expectedList = List.of(
                new RackResponse(UUID.randomUUID(), warehouseId, RackKind.SHELF, "Rack 1", true, LocalDateTime.now(), LocalDateTime.now())
        );

        when(rackService.getRacksByWarehouse(warehouseId)).thenReturn(expectedList);

        ResponseEntity<List<RackResponse>> response = rackController.getRacksByWarehouse(warehouseId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        verify(rackService).getRacksByWarehouse(warehouseId);
    }

    @Test
    @DisplayName("getRack: Should return rack")
    void getRack_ShouldReturnRack() {
        UUID rackId = UUID.randomUUID();
        RackResponse expectedResponse = new RackResponse(
                rackId,
                UUID.randomUUID(),
                RackKind.SHELF,
                "Rack A1",
                true,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(rackService.getRack(rackId)).thenReturn(expectedResponse);

        ResponseEntity<RackResponse> response = rackController.getRack(rackId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        verify(rackService).getRack(rackId);
    }

    @Test
    @DisplayName("getCellInfo: Should return cell info")
    void getCellInfo_ShouldReturnCellInfo() {
        UUID cellId = UUID.randomUUID();
        Map<String, Object> expectedInfo = Map.of("cellId", cellId);

        when(rackService.getCellInfo(cellId)).thenReturn(expectedInfo);

        ResponseEntity<Object> response = rackController.getCellInfo(cellId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(expectedInfo);
        verify(rackService).getCellInfo(cellId);
    }
}

