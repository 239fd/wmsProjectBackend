package by.bsuir.warehouseservice.service;

import by.bsuir.warehouseservice.dto.request.*;
import by.bsuir.warehouseservice.dto.response.RackResponse;
import by.bsuir.warehouseservice.exception.AppException;
import by.bsuir.warehouseservice.model.entity.*;
import by.bsuir.warehouseservice.model.enums.RackKind;
import by.bsuir.warehouseservice.repository.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RackService Unit Tests")
class RackServiceTest {

    @Mock
    private RackReadModelRepository rackRepository;

    @Mock
    private RackEventRepository eventRepository;

    @Mock
    private ShelfRepository shelfRepository;

    @Mock
    private CellRepository cellRepository;

    @Mock
    private FridgeRepository fridgeRepository;

    @Mock
    private PalletRepository palletRepository;

    @Mock
    private PalletPlaceRepository palletPlaceRepository;

    @Mock
    private WarehouseReadModelRepository warehouseRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RackService rackService;

    @Test
    @DisplayName("createRack: Given valid request Should create and return rack")
    void createRack_GivenValidRequest_ShouldCreateAndReturnRack() {
        UUID warehouseId = UUID.randomUUID();
        CreateRackRequest request = new CreateRackRequest(
                warehouseId,
                RackKind.SHELF,
                "Rack A1"
        );

        WarehouseReadModel warehouse = WarehouseReadModel.builder()
                .warehouseId(warehouseId)
                .name("Test Warehouse")
                .build();

        when(warehouseRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(warehouse));
        when(objectMapper.valueToTree(any())).thenReturn(null);
        when(eventRepository.save(any(RackEvent.class))).thenReturn(null);
        when(rackRepository.save(any(RackReadModel.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RackResponse response = rackService.createRack(request);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("Rack A1");
        assertThat(response.kind()).isEqualTo(RackKind.SHELF);
        assertThat(response.warehouseId()).isEqualTo(warehouseId);

        verify(warehouseRepository).findByWarehouseId(warehouseId);
        verify(eventRepository).save(any(RackEvent.class));
        verify(rackRepository).save(any(RackReadModel.class));
    }

    @Test
    @DisplayName("createRack: Given non-existing warehouse Should throw not found exception")
    void createRack_GivenNonExistingWarehouse_ShouldThrowNotFoundException() {
        UUID warehouseId = UUID.randomUUID();
        CreateRackRequest request = new CreateRackRequest(
                warehouseId,
                RackKind.SHELF,
                "Rack A1"
        );

        when(warehouseRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rackService.createRack(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("Склад не найден");

        verify(warehouseRepository).findByWarehouseId(warehouseId);
        verify(rackRepository, never()).save(any());
    }

    @Test
    @DisplayName("getRacksByWarehouse: Should return list of racks")
    void getRacksByWarehouse_ShouldReturnListOfRacks() {
        UUID warehouseId = UUID.randomUUID();

        RackReadModel rack1 = RackReadModel.builder()
                .rackId(UUID.randomUUID())
                .warehouseId(warehouseId)
                .kind(RackKind.SHELF)
                .name("Rack 1")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        RackReadModel rack2 = RackReadModel.builder()
                .rackId(UUID.randomUUID())
                .warehouseId(warehouseId)
                .kind(RackKind.PALLET)
                .name("Rack 2")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(rackRepository.findByWarehouseId(warehouseId))
                .thenReturn(List.of(rack1, rack2));

        List<RackResponse> responses = rackService.getRacksByWarehouse(warehouseId);

        assertThat(responses).hasSize(2);
        verify(rackRepository).findByWarehouseId(warehouseId);
    }

    @Test
    @DisplayName("getRacksByWarehouse: Given no racks Should return empty list")
    void getRacksByWarehouse_GivenNoRacks_ShouldReturnEmptyList() {
        UUID warehouseId = UUID.randomUUID();

        when(rackRepository.findByWarehouseId(warehouseId)).thenReturn(List.of());

        List<RackResponse> responses = rackService.getRacksByWarehouse(warehouseId);

        assertThat(responses).isEmpty();
        verify(rackRepository).findByWarehouseId(warehouseId);
    }

    @Test
    @DisplayName("createShelf: Given valid request Should create shelf")
    void createShelf_GivenValidRequest_ShouldCreateShelf() {
        UUID rackId = UUID.randomUUID();
        CreateShelfRequest request = new CreateShelfRequest(
                rackId,
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(200.0),
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(50.0)
        );

        RackReadModel rack = RackReadModel.builder()
                .rackId(rackId)
                .kind(RackKind.SHELF)
                .name("Shelf Rack")
                .isActive(true)
                .build();

        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));
        when(shelfRepository.save(any(Shelf.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rackService.createShelf(request);

        verify(rackRepository).findById(rackId);
        verify(shelfRepository).save(any(Shelf.class));
    }

    @Test
    @DisplayName("createShelf: Given wrong rack kind Should throw exception")
    void createShelf_GivenWrongRackKind_ShouldThrowException() {
        UUID rackId = UUID.randomUUID();
        CreateShelfRequest request = new CreateShelfRequest(
                rackId,
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(200.0),
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(50.0)
        );

        RackReadModel rack = RackReadModel.builder()
                .rackId(rackId)
                .kind(RackKind.PALLET)
                .name("Pallet Rack")
                .isActive(true)
                .build();

        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));

        assertThatThrownBy(() -> rackService.createShelf(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("SHELF");

        verify(shelfRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCell: Given valid request Should create cell")
    void createCell_GivenValidRequest_ShouldCreateCell() {
        UUID rackId = UUID.randomUUID();
        CreateCellRequest request = new CreateCellRequest(
                rackId,
                BigDecimal.valueOf(50.0),
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(100.0),
                BigDecimal.valueOf(100.0)
        );

        RackReadModel rack = RackReadModel.builder()
                .rackId(rackId)
                .kind(RackKind.CELL)
                .name("Cell Rack")
                .isActive(true)
                .build();

        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));
        when(cellRepository.save(any(Cell.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rackService.createCell(request);

        verify(rackRepository).findById(rackId);
        verify(cellRepository).save(any(Cell.class));
    }

    @Test
    @DisplayName("createFridge: Given valid request Should create fridge")
    void createFridge_GivenValidRequest_ShouldCreateFridge() {
        UUID rackId = UUID.randomUUID();
        CreateFridgeRequest request = new CreateFridgeRequest(
                rackId,
                BigDecimal.valueOf(-18.0),
                BigDecimal.valueOf(200.0),
                BigDecimal.valueOf(150.0),
                BigDecimal.valueOf(200.0)
        );

        RackReadModel rack = RackReadModel.builder()
                .rackId(rackId)
                .kind(RackKind.FRIDGE)
                .name("Fridge Rack")
                .isActive(true)
                .build();

        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));
        when(fridgeRepository.existsById(rackId)).thenReturn(false);
        when(fridgeRepository.save(any(Fridge.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rackService.createFridge(request);

        verify(rackRepository).findById(rackId);
        verify(fridgeRepository).save(any(Fridge.class));
    }

    @Test
    @DisplayName("createFridge: Given existing fridge Should throw exception")
    void createFridge_GivenExistingFridge_ShouldThrowException() {
        UUID rackId = UUID.randomUUID();
        CreateFridgeRequest request = new CreateFridgeRequest(
                rackId,
                BigDecimal.valueOf(-18.0),
                BigDecimal.valueOf(200.0),
                BigDecimal.valueOf(150.0),
                BigDecimal.valueOf(200.0)
        );

        RackReadModel rack = RackReadModel.builder()
                .rackId(rackId)
                .kind(RackKind.FRIDGE)
                .name("Fridge Rack")
                .isActive(true)
                .build();

        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));
        when(fridgeRepository.existsById(rackId)).thenReturn(true);

        assertThatThrownBy(() -> rackService.createFridge(request))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("уже создан");

        verify(fridgeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createPallet: Given valid request Should create pallet")
    void createPallet_GivenValidRequest_ShouldCreatePallet() {
        UUID rackId = UUID.randomUUID();
        CreatePalletRequest request = new CreatePalletRequest(
                rackId,
                10,
                BigDecimal.valueOf(1000.0)
        );

        RackReadModel rack = RackReadModel.builder()
                .rackId(rackId)
                .kind(RackKind.PALLET)
                .name("Pallet Rack")
                .isActive(true)
                .build();

        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));
        when(palletRepository.existsById(rackId)).thenReturn(false);
        when(palletRepository.save(any(Pallet.class))).thenAnswer(invocation -> invocation.getArgument(0));

        rackService.createPallet(request);

        verify(rackRepository).findById(rackId);
        verify(palletRepository).save(any(Pallet.class));
    }

    @Test
    @DisplayName("getRack: Given existing rack Should return rack")
    void getRack_GivenExistingRack_ShouldReturnRack() {
        UUID rackId = UUID.randomUUID();

        RackReadModel rack = RackReadModel.builder()
                .rackId(rackId)
                .warehouseId(UUID.randomUUID())
                .kind(RackKind.SHELF)
                .name("Test Rack")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));

        RackResponse response = rackService.getRack(rackId);

        assertThat(response).isNotNull();
        assertThat(response.rackId()).isEqualTo(rackId);
        assertThat(response.name()).isEqualTo("Test Rack");
        verify(rackRepository).findById(rackId);
    }

    @Test
    @DisplayName("getRack: Given non-existing rack Should throw exception")
    void getRack_GivenNonExistingRack_ShouldThrowException() {
        UUID rackId = UUID.randomUUID();

        when(rackRepository.findById(rackId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> rackService.getRack(rackId))
                .isInstanceOf(AppException.class)
                .hasMessageContaining("не найден");

        verify(rackRepository).findById(rackId);
    }

    @Test
    @DisplayName("getCellInfo: Given existing shelf Should return shelf")
    void getCellInfo_GivenExistingShelf_ShouldReturnShelf() {
        UUID cellId = UUID.randomUUID();

        Shelf shelf = Shelf.builder()
                .shelfId(cellId)
                .rackId(UUID.randomUUID())
                .shelfCapacityKg(BigDecimal.valueOf(100.0))
                .build();

        when(shelfRepository.findById(cellId)).thenReturn(Optional.of(shelf));

        Object result = rackService.getCellInfo(cellId);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(Shelf.class);
        verify(shelfRepository).findById(cellId);
    }

    @Test
    @DisplayName("getCellInfo: Given existing cell Should return cell")
    void getCellInfo_GivenExistingCell_ShouldReturnCell() {
        UUID cellId = UUID.randomUUID();

        Cell cell = Cell.builder()
                .cellId(cellId)
                .rackId(UUID.randomUUID())
                .maxWeightKg(BigDecimal.valueOf(50.0))
                .build();

        when(shelfRepository.findById(cellId)).thenReturn(Optional.empty());
        when(cellRepository.findById(cellId)).thenReturn(Optional.of(cell));

        Object result = rackService.getCellInfo(cellId);

        assertThat(result).isNotNull();
        assertThat(result).isInstanceOf(Cell.class);
        verify(cellRepository).findById(cellId);
    }
}

