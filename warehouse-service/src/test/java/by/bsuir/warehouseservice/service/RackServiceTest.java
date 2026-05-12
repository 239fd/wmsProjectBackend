package by.bsuir.warehouseservice.service;

import by.bsuir.warehouseservice.dto.request.CreateCellRequest;
import by.bsuir.warehouseservice.dto.request.CreateFridgeRequest;
import by.bsuir.warehouseservice.dto.request.CreatePalletRequest;
import by.bsuir.warehouseservice.dto.request.CreateRackRequest;
import by.bsuir.warehouseservice.dto.request.CreateShelfRequest;
import by.bsuir.warehouseservice.dto.response.RackResponse;
import by.bsuir.warehouseservice.exception.AppException;
import by.bsuir.warehouseservice.model.entity.Cell;
import by.bsuir.warehouseservice.model.entity.Fridge;
import by.bsuir.warehouseservice.model.entity.Pallet;
import by.bsuir.warehouseservice.model.entity.PalletPlace;
import by.bsuir.warehouseservice.model.entity.RackEvent;
import by.bsuir.warehouseservice.model.entity.RackReadModel;
import by.bsuir.warehouseservice.model.entity.Shelf;
import by.bsuir.warehouseservice.model.entity.WarehouseReadModel;
import by.bsuir.warehouseservice.model.enums.PalletType;
import by.bsuir.warehouseservice.model.enums.RackKind;
import by.bsuir.warehouseservice.repository.CellRepository;
import by.bsuir.warehouseservice.repository.FridgeRepository;
import by.bsuir.warehouseservice.repository.PalletPlaceRepository;
import by.bsuir.warehouseservice.repository.PalletRepository;
import by.bsuir.warehouseservice.repository.RackEventRepository;
import by.bsuir.warehouseservice.repository.RackReadModelRepository;
import by.bsuir.warehouseservice.repository.ShelfRepository;
import by.bsuir.warehouseservice.repository.WarehouseReadModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RackService — модульные тесты")
class RackServiceTest {

    @Mock private RackReadModelRepository rackRepository;
    @Mock private RackEventRepository eventRepository;
    @Mock private ShelfRepository shelfRepository;
    @Mock private CellRepository cellRepository;
    @Mock private FridgeRepository fridgeRepository;
    @Mock private PalletRepository palletRepository;
    @Mock private PalletPlaceRepository palletPlaceRepository;
    @Mock private WarehouseReadModelRepository warehouseRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private RackService rackService;

    @Test
    @DisplayName("createRack: валидный → сохраняет event + read model, возвращает response")
    void createRack_GivenValid_ShouldPersistAndReturn() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findByWarehouseId(warehouseId))
                .thenReturn(Optional.of(WarehouseReadModel.builder()
                        .warehouseId(warehouseId).orgId(UUID.randomUUID()).build()));
        CreateRackRequest req = new CreateRackRequest(warehouseId, RackKind.SHELF, "A-1", null);

        RackResponse response = rackService.createRack(req);

        assertThat(response).isNotNull();
        assertThat(response.name()).isEqualTo("A-1");
        assertThat(response.kind()).isEqualTo(RackKind.SHELF);
        assertThat(response.warehouseId()).isEqualTo(warehouseId);
        verify(eventRepository).save(any(RackEvent.class));
        verify(rackRepository).save(any(RackReadModel.class));
    }

    @Test
    @DisplayName("createRack: склад не найден → 404")
    void createRack_GivenMissingWarehouse_ShouldThrowNotFound() {
        UUID warehouseId = UUID.randomUUID();
        when(warehouseRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.empty());
        CreateRackRequest req = new CreateRackRequest(warehouseId, RackKind.SHELF, "A-1", null);

        AppException ex = catchApp(() -> rackService.createRack(req));
        assertThat(ex.getMessage()).contains("Склад не найден");
        verify(rackRepository, never()).save(any());
    }

    @Test
    @DisplayName("createShelf: тип SHELF + склад с orgId → сохраняет Shelf с organizationId денормализованным")
    void createShelf_GivenShelfRack_ShouldPersistWithOrgId() {
        UUID rackId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        UUID orgId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(
                RackReadModel.builder().rackId(rackId).warehouseId(warehouseId)
                        .kind(RackKind.SHELF).build()));
        when(warehouseRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(
                WarehouseReadModel.builder().warehouseId(warehouseId).orgId(orgId).build()));
        when(shelfRepository.save(any(Shelf.class))).thenAnswer(inv -> {
            Shelf s = inv.getArgument(0);
            if (s.getShelfId() == null) s.setShelfId(UUID.randomUUID());
            return s;
        });
        when(eventRepository.findMaxVersionByRackId(rackId)).thenReturn(0);

        CreateShelfRequest req = new CreateShelfRequest(rackId,
                BigDecimal.valueOf(100), BigDecimal.valueOf(50), BigDecimal.valueOf(40), BigDecimal.valueOf(30));
        rackService.createShelf(req);

        org.mockito.ArgumentCaptor<Shelf> captor = org.mockito.ArgumentCaptor.forClass(Shelf.class);
        verify(shelfRepository).save(captor.capture());
        assertThat(captor.getValue().getRackId()).isEqualTo(rackId);
        assertThat(captor.getValue().getOrganizationId()).isEqualTo(orgId);
        assertThat(captor.getValue().getShelfCapacityKg()).isEqualTo(BigDecimal.valueOf(100));
        verify(eventRepository).save(any(RackEvent.class));
    }

    @Test
    @DisplayName("createShelf: тип CELL → 400 bad request, save не вызывается")
    void createShelf_GivenWrongKind_ShouldThrowBadRequest() {
        UUID rackId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(
                RackReadModel.builder().rackId(rackId).kind(RackKind.CELL).build()));
        CreateShelfRequest req = new CreateShelfRequest(rackId,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);

        AppException ex = catchApp(() -> rackService.createShelf(req));
        assertThat(ex.getMessage()).contains("SHELF");
        verify(shelfRepository, never()).save(any());
    }

    @Test
    @DisplayName("createCell: тип CELL → сохраняет ячейку")
    void createCell_GivenCellRack_ShouldPersist() {
        UUID rackId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(
                RackReadModel.builder().rackId(rackId).warehouseId(warehouseId)
                        .kind(RackKind.CELL).build()));
        when(warehouseRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(
                WarehouseReadModel.builder().warehouseId(warehouseId).orgId(UUID.randomUUID()).build()));
        when(cellRepository.save(any(Cell.class))).thenAnswer(inv -> {
            Cell c = inv.getArgument(0);
            if (c.getCellId() == null) c.setCellId(UUID.randomUUID());
            return c;
        });
        when(eventRepository.findMaxVersionByRackId(rackId)).thenReturn(0);

        CreateCellRequest req = new CreateCellRequest(rackId,
                BigDecimal.valueOf(50), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN);
        rackService.createCell(req);

        verify(cellRepository).save(any(Cell.class));
        verify(eventRepository).save(any(RackEvent.class));
    }

    @Test
    @DisplayName("createFridge: min > max → 400 bad request")
    void createFridge_GivenInvalidTempRange_ShouldThrowBadRequest() {
        UUID rackId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(
                RackReadModel.builder().rackId(rackId).kind(RackKind.FRIDGE).build()));
        when(fridgeRepository.existsById(rackId)).thenReturn(false);

        CreateFridgeRequest req = new CreateFridgeRequest(rackId,
                BigDecimal.valueOf(10), BigDecimal.valueOf(2),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);

        AppException ex = catchApp(() -> rackService.createFridge(req));
        assertThat(ex.getMessage()).contains("Минимальная температура");
        verify(fridgeRepository, never()).save(any());
    }

    @Test
    @DisplayName("createFridge: уже существует → 409 conflict")
    void createFridge_WhenAlreadyExists_ShouldThrowConflict() {
        UUID rackId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(
                RackReadModel.builder().rackId(rackId).kind(RackKind.FRIDGE).build()));
        when(fridgeRepository.existsById(rackId)).thenReturn(true);

        CreateFridgeRequest req = new CreateFridgeRequest(rackId,
                BigDecimal.valueOf(-20), BigDecimal.valueOf(-5),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);

        AppException ex = catchApp(() -> rackService.createFridge(req));
        assertThat(ex.getMessage()).contains("уже создан");
    }

    @Test
    @DisplayName("createFridge: валидные параметры → сохраняет Fridge")
    void createFridge_GivenValid_ShouldPersist() {
        UUID rackId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(
                RackReadModel.builder().rackId(rackId).warehouseId(warehouseId)
                        .kind(RackKind.FRIDGE).build()));
        when(warehouseRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(
                WarehouseReadModel.builder().warehouseId(warehouseId).orgId(UUID.randomUUID()).build()));
        when(fridgeRepository.existsById(rackId)).thenReturn(false);
        when(eventRepository.findMaxVersionByRackId(rackId)).thenReturn(0);

        CreateFridgeRequest req = new CreateFridgeRequest(rackId,
                BigDecimal.valueOf(-20), BigDecimal.valueOf(-5),
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);
        rackService.createFridge(req);

        verify(fridgeRepository).save(any(Fridge.class));
    }

    @Test
    @DisplayName("createPallet: создаёт N паллетомест и Pallet-агрегат")
    void createPallet_ShouldCreatePalletAndPlaces() {
        UUID rackId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(
                RackReadModel.builder().rackId(rackId).warehouseId(warehouseId)
                        .kind(RackKind.PALLET).build()));
        when(warehouseRepository.findByWarehouseId(warehouseId)).thenReturn(Optional.of(
                WarehouseReadModel.builder().warehouseId(warehouseId).orgId(UUID.randomUUID()).build()));
        when(palletRepository.existsById(rackId)).thenReturn(false);
        when(eventRepository.findMaxVersionByRackId(rackId)).thenReturn(0);

        CreatePalletRequest req = new CreatePalletRequest(rackId, 5, BigDecimal.valueOf(1000), PalletType.EUR);
        rackService.createPallet(req);

        verify(palletRepository).save(any(Pallet.class));
        verify(palletPlaceRepository, times(5)).save(any(PalletPlace.class));
        verify(eventRepository).save(any(RackEvent.class));
    }

    @Test
    @DisplayName("createPallet: уже существует → 409 conflict")
    void createPallet_WhenAlreadyExists_ShouldThrowConflict() {
        UUID rackId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(
                RackReadModel.builder().rackId(rackId).kind(RackKind.PALLET).build()));
        when(palletRepository.existsById(rackId)).thenReturn(true);

        CreatePalletRequest req = new CreatePalletRequest(rackId, 1, BigDecimal.ONE, PalletType.EUR);
        AppException ex = catchApp(() -> rackService.createPallet(req));
        assertThat(ex.getMessage()).contains("уже создан");
        verify(palletPlaceRepository, never()).save(any());
    }

    @Test
    @DisplayName("getRacksByWarehouse: маппит в response-list")
    void getRacksByWarehouse_ShouldMap() {
        UUID warehouseId = UUID.randomUUID();
        when(rackRepository.findByWarehouseId(warehouseId)).thenReturn(List.of(
                RackReadModel.builder().rackId(UUID.randomUUID()).warehouseId(warehouseId)
                        .kind(RackKind.SHELF).name("A-1").isActive(true).build(),
                RackReadModel.builder().rackId(UUID.randomUUID()).warehouseId(warehouseId)
                        .kind(RackKind.CELL).name("B-1").isActive(true).build()));

        List<RackResponse> all = rackService.getRacksByWarehouse(warehouseId);

        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("getRack: не найден → 404")
    void getRack_GivenMissing_ShouldThrowNotFound() {
        UUID rackId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.empty());

        AppException ex = catchApp(() -> rackService.getRack(rackId));
        assertThat(ex.getMessage()).contains("Стеллаж не найден");
    }

    @Test
    @DisplayName("getSlotsByRack: SHELF → возвращает {kind:SHELF, slots:[]} с полками")
    void getSlotsByRack_GivenShelfRack_ShouldReturnShelves() {
        UUID rackId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(
                RackReadModel.builder().rackId(rackId).kind(RackKind.SHELF).name("A-1").build()));
        Shelf s1 = Shelf.builder().shelfId(UUID.randomUUID()).rackId(rackId)
                .shelfCapacityKg(BigDecimal.TEN).lengthCm(BigDecimal.ONE)
                .widthCm(BigDecimal.ONE).heightCm(BigDecimal.ONE).build();
        when(shelfRepository.findByRackId(rackId)).thenReturn(List.of(s1));

        Map<String, Object> result = rackService.getSlotsByRack(rackId);

        assertThat(result.get("kind")).isEqualTo("SHELF");
        assertThat(result.get("rackName")).isEqualTo("A-1");
        @SuppressWarnings("unchecked")
        List<Object> slots = (List<Object>) result.get("slots");
        assertThat(slots).hasSize(1);
    }

    @Test
    @DisplayName("getSlotsByRack: PALLET → возвращает паллет-места")
    void getSlotsByRack_GivenPalletRack_ShouldReturnPalletPlaces() {
        UUID rackId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(
                RackReadModel.builder().rackId(rackId).kind(RackKind.PALLET).name("P-1").build()));
        when(palletPlaceRepository.findByRackId(rackId)).thenReturn(List.of(
                PalletPlace.builder().placeId(UUID.randomUUID()).rackId(rackId).build(),
                PalletPlace.builder().placeId(UUID.randomUUID()).rackId(rackId).build()));

        Map<String, Object> result = rackService.getSlotsByRack(rackId);

        assertThat(result.get("kind")).isEqualTo("PALLET");
        @SuppressWarnings("unchecked")
        List<Object> slots = (List<Object>) result.get("slots");
        assertThat(slots).hasSize(2);
    }

    @Test
    @DisplayName("getSlotsByRack: rack не найден → 404")
    void getSlotsByRack_GivenMissingRack_ShouldThrowNotFound() {
        UUID rackId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.empty());

        AppException ex = catchApp(() -> rackService.getSlotsByRack(rackId));
        assertThat(ex.getMessage()).contains("Стеллаж не найден");
    }

    @Test
    @DisplayName("deleteRack: существующий → удаляет и пишет RACK_DELETED событие")
    void deleteRack_GivenExisting_ShouldDeleteAndEvent() {
        UUID rackId = UUID.randomUUID();
        UUID warehouseId = UUID.randomUUID();
        RackReadModel rack = RackReadModel.builder().rackId(rackId).warehouseId(warehouseId)
                .kind(RackKind.SHELF).build();
        when(rackRepository.findById(rackId)).thenReturn(Optional.of(rack));

        rackService.deleteRack(rackId);

        verify(rackRepository).delete(rack);
        verify(eventRepository).save(any(RackEvent.class));
    }

    @Test
    @DisplayName("deleteRack: не найден → 404")
    void deleteRack_GivenMissing_ShouldThrowNotFound() {
        UUID rackId = UUID.randomUUID();
        when(rackRepository.findById(rackId)).thenReturn(Optional.empty());

        AppException ex = catchApp(() -> rackService.deleteRack(rackId));
        assertThat(ex.getMessage()).contains("Стеллаж не найден");
        verify(rackRepository, never()).delete(any(RackReadModel.class));
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
