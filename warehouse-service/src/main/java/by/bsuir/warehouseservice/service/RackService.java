package by.bsuir.warehouseservice.service;

import by.bsuir.warehouseservice.dto.request.CreateCellRequest;
import by.bsuir.warehouseservice.dto.request.CreatePalletRequest;
import by.bsuir.warehouseservice.dto.request.CreateRackRequest;
import by.bsuir.warehouseservice.dto.request.CreateShelfRequest;
import by.bsuir.warehouseservice.dto.response.RackResponse;
import by.bsuir.warehouseservice.exception.AppException;
import by.bsuir.warehouseservice.model.entity.Cell;
import by.bsuir.warehouseservice.model.entity.Pallet;
import by.bsuir.warehouseservice.model.entity.PalletPlace;
import by.bsuir.warehouseservice.model.entity.RackEvent;
import by.bsuir.warehouseservice.model.entity.RackReadModel;
import by.bsuir.warehouseservice.model.entity.Shelf;
import by.bsuir.warehouseservice.model.enums.PalletType;
import by.bsuir.warehouseservice.model.enums.RackKind;
import by.bsuir.warehouseservice.repository.CellRepository;
import by.bsuir.warehouseservice.repository.PalletPlaceRepository;
import by.bsuir.warehouseservice.repository.PalletRepository;
import by.bsuir.warehouseservice.repository.RackEventRepository;
import by.bsuir.warehouseservice.repository.RackReadModelRepository;
import by.bsuir.warehouseservice.repository.ShelfRepository;
import by.bsuir.warehouseservice.client.ProductClient;
import by.bsuir.warehouseservice.repository.WarehouseReadModelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RackService {

    private final RackReadModelRepository rackRepository;
    private final RackEventRepository eventRepository;
    private final ShelfRepository shelfRepository;
    private final CellRepository cellRepository;
    private final PalletRepository palletRepository;
    private final PalletPlaceRepository palletPlaceRepository;
    private final ProductClient productClient;
    private final WarehouseReadModelRepository warehouseRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public RackResponse createRack(CreateRackRequest request) {
        log.info("Creating rack: {} of type: {} for warehouse: {}",
                request.name(), request.kind(), request.warehouseId());

        warehouseRepository.findByWarehouseId(request.warehouseId())
                .orElseThrow(() -> AppException.notFound("Склад не найден"));

        UUID rackId = UUID.randomUUID();

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("warehouseId", request.warehouseId().toString());
        eventData.put("kind", request.kind().name());
        eventData.put("name", request.name());

        RackEvent rackEvent = RackEvent.builder()
                .rackId(rackId)
                .eventType("RACK_CREATED")
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(rackEvent);

        RackReadModel readModel = RackReadModel.builder()
                .rackId(rackId)
                .warehouseId(request.warehouseId())
                .kind(request.kind())
                .name(request.name())
                .storageConditions(request.storageConditions())
                .maxWeightKg(request.maxWeightKg())
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        rackRepository.save(readModel);

        log.info("Rack created successfully with ID: {}", rackId);
        return mapToResponse(readModel);
    }

    @Transactional
    public void createShelf(CreateShelfRequest request) {
        log.info("Creating shelf for rack: {}", request.rackId());

        RackReadModel rack = rackRepository.findById(request.rackId())
                .orElseThrow(() -> AppException.notFound("Стеллаж не найден"));

        if (rack.getKind() != RackKind.SHELF) {
            throw AppException.badRequest("Стеллаж должен иметь тип SHELF");
        }

        Shelf shelf = Shelf.builder()
                .rackId(request.rackId())
                .warehouseId(rack.getWarehouseId())
                .slotCode(generateSlotCode(rack, shelfRepository.countByRackId(rack.getRackId())))
                .organizationId(resolveOrgIdForRack(rack))
                .shelfCapacityKg(request.shelfCapacityKg())
                .lengthCm(request.lengthCm())
                .widthCm(request.widthCm())
                .heightCm(request.heightCm())
                .build();

        shelfRepository.save(shelf);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("shelfId", shelf.getShelfId().toString());
        eventData.put("shelfCapacityKg", request.shelfCapacityKg());
        eventData.put("lengthCm", request.lengthCm());
        eventData.put("widthCm", request.widthCm());
        eventData.put("heightCm", request.heightCm());
        saveRackEvent(request.rackId(), "SHELF_CREATED", eventData);

        log.info("Shelf created successfully with ID: {}", shelf.getShelfId());
    }

    @Transactional
    public void createCell(CreateCellRequest request) {
        log.info("Creating cell for rack: {}", request.rackId());

        RackReadModel rack = rackRepository.findById(request.rackId())
                .orElseThrow(() -> AppException.notFound("Стеллаж не найден"));

        if (rack.getKind() != RackKind.CELL) {
            throw AppException.badRequest("Стеллаж должен иметь тип CELL");
        }

        Cell cell = Cell.builder()
                .rackId(request.rackId())
                .warehouseId(rack.getWarehouseId())
                .slotCode(generateSlotCode(rack, cellRepository.countByRackId(rack.getRackId())))
                .organizationId(resolveOrgIdForRack(rack))
                .maxWeightKg(request.maxWeightKg())
                .lengthCm(request.lengthCm())
                .widthCm(request.widthCm())
                .heightCm(request.heightCm())
                .build();

        cellRepository.save(cell);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("cellId", cell.getCellId().toString());
        eventData.put("maxWeightKg", request.maxWeightKg());
        eventData.put("lengthCm", request.lengthCm());
        eventData.put("widthCm", request.widthCm());
        eventData.put("heightCm", request.heightCm());
        saveRackEvent(request.rackId(), "CELL_CREATED", eventData);

        log.info("Cell created successfully with ID: {}", cell.getCellId());
    }

    @Transactional
    public void createPallet(CreatePalletRequest request) {
        log.info("Creating pallet for rack: {}", request.rackId());

        RackReadModel rack = rackRepository.findById(request.rackId())
                .orElseThrow(() -> AppException.notFound("Стеллаж не найден"));

        if (rack.getKind() != RackKind.PALLET) {
            throw AppException.badRequest("Стеллаж должен иметь тип PALLET");
        }

        if (palletRepository.existsById(request.rackId())) {
            throw AppException.conflict("Паллет уже создан для этого стеллажа");
        }

        Pallet pallet = Pallet.builder()
                .rackId(request.rackId())
                .palletPlaceCount(request.palletPlaceCount())
                .maxWeightKg(request.maxWeightKg())
                .build();

        palletRepository.save(pallet);

        PalletType palletType = request.palletType();
        UUID rackOrgId = resolveOrgIdForRack(rack);
        long existing = palletPlaceRepository.countByRackId(request.rackId());
        for (int i = 0; i < request.palletPlaceCount(); i++) {
            PalletPlace place = PalletPlace.builder()
                    .placeId(UUID.randomUUID())
                    .rackId(request.rackId())
                    .warehouseId(rack.getWarehouseId())
                    .slotCode(generateSlotCode(rack, existing + i))
                    .organizationId(rackOrgId)
                    .lengthCm(palletType.getLengthCm())
                    .widthCm(palletType.getWidthCm())
                    .heightCm(palletType.getHeightCm())
                    .maxHeightCm(request.maxHeightCm())
                    .build();
            palletPlaceRepository.save(place);
        }

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("palletPlaceCount", request.palletPlaceCount());
        eventData.put("maxWeightKg", request.maxWeightKg());
        eventData.put("maxHeightCm", request.maxHeightCm());
        eventData.put("palletType", palletType.name());
        saveRackEvent(request.rackId(), "PALLET_CREATED", eventData);

        log.info("Pallet created successfully for rack: {} with {} places of type {}",
                request.rackId(), request.palletPlaceCount(), palletType);
    }


    private UUID resolveOrgIdForRack(RackReadModel rack) {
        if (rack == null || rack.getWarehouseId() == null) return null;
        return warehouseRepository.findByWarehouseId(rack.getWarehouseId())
                .map(w -> w.getOrgId())
                .orElse(null);
    }

    private String generateSlotCode(RackReadModel rack, long existingCount) {
        String base = rack.getName() != null && !rack.getName().isBlank()
                ? rack.getName().trim()
                : "R" + rack.getRackId().toString().substring(0, 4).toUpperCase();
        return base + "-" + (existingCount + 1);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolveSlotByCode(UUID warehouseId, String slotCode) {
        if (warehouseId == null || slotCode == null || slotCode.isBlank()) {
            throw AppException.badRequest("warehouseId и код ячейки обязательны");
        }
        String code = slotCode.trim();

        Optional<Cell> cell = cellRepository.findByWarehouseIdAndSlotCode(warehouseId, code);
        if (cell.isPresent()) {
            Map<String, Object> row = cellToMap(cell.get());
            rackRepository.findById(cell.get().getRackId()).ifPresent(r -> annotateRack(row, r));
            return row;
        }
        Optional<Shelf> shelf = shelfRepository.findByWarehouseIdAndSlotCode(warehouseId, code);
        if (shelf.isPresent()) {
            Map<String, Object> row = shelfToMap(shelf.get());
            rackRepository.findById(shelf.get().getRackId()).ifPresent(r -> annotateRack(row, r));
            return row;
        }
        Optional<PalletPlace> place = palletPlaceRepository.findByWarehouseIdAndSlotCode(warehouseId, code);
        if (place.isPresent()) {
            Map<String, Object> row = palletPlaceToMap(place.get());
            rackRepository.findById(place.get().getRackId()).ifPresent(r -> annotateRack(row, r));
            return row;
        }
        throw AppException.notFound("Ячейка с кодом «" + code + "» не найдена на складе");
    }

    private void saveRackEvent(UUID rackId, String eventType, Map<String, Object> eventData) {
        Integer lastVersion = eventRepository.findMaxVersionByRackId(rackId);
        int nextVersion = (lastVersion == null ? 0 : lastVersion) + 1;

        RackEvent event = RackEvent.builder()
                .rackId(rackId)
                .eventType(eventType)
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(nextVersion)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public List<RackResponse> getRacksByWarehouse(UUID warehouseId) {
        return rackRepository.findByWarehouseId(warehouseId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<RackResponse> getRacksByWarehouse(UUID warehouseId, Pageable pageable) {
        return rackRepository.findByWarehouseId(warehouseId, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public RackResponse getRack(UUID rackId) {
        RackReadModel rack = rackRepository.findById(rackId)
                .orElseThrow(() -> AppException.notFound("Стеллаж не найден"));
        return mapToResponse(rack);
    }

    @Transactional(readOnly = true)
    public Object getCellInfo(UUID cellId) {

        Optional<Shelf> shelf = shelfRepository.findById(cellId);
        if (shelf.isPresent()) {
            return shelf.get();
        }

        Optional<Cell> cell = cellRepository.findById(cellId);
        if (cell.isPresent()) {
            return cell.get();
        }

        Optional<PalletPlace> palletPlace = palletPlaceRepository.findById(cellId);
        if (palletPlace.isPresent()) {
            return palletPlace.get();
        }

        throw AppException.notFound("Ячейка не найдена");
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAllCellsFlat(UUID warehouseId) {
        List<RackReadModel> racks = rackRepository.findByWarehouseId(warehouseId);
        List<Map<String, Object>> result = new ArrayList<>();
        List<UUID> allSlotIds = new ArrayList<>();
        for (RackReadModel rack : racks) {
            if (!Boolean.TRUE.equals(rack.getIsActive())) continue;
            switch (rack.getKind()) {
                case SHELF:
                    for (Shelf s : shelfRepository.findByRackId(rack.getRackId())) {
                        Map<String, Object> row = shelfToMap(s);
                        annotateRack(row, rack);
                        result.add(row);
                        allSlotIds.add(s.getShelfId());
                    }
                    break;
                case CELL:
                    for (Cell c : cellRepository.findByRackId(rack.getRackId())) {
                        Map<String, Object> row = cellToMap(c);
                        annotateRack(row, rack);
                        result.add(row);
                        allSlotIds.add(c.getCellId());
                    }
                    break;
                case PALLET:
                    for (PalletPlace p : palletPlaceRepository.findByRackId(rack.getRackId())) {
                        Map<String, Object> row = palletPlaceToMap(p);
                        annotateRack(row, rack);
                        result.add(row);
                        allSlotIds.add(p.getPlaceId());
                    }
                    break;
            }
        }
        Map<UUID, ProductClient.CellLoad> loads = productClient.getCellsLoad(allSlotIds);
        for (Map<String, Object> row : result) {
            UUID id = (UUID) row.get("id");
            ProductClient.CellLoad load = loads.get(id);
            int itemsCount = load == null ? 0 : load.itemsCount();
            boolean occupied = load != null && load.occupied();
            row.put("itemsCount", itemsCount);
            row.put("totalQuantity", load == null ? java.math.BigDecimal.ZERO : load.totalQuantity());
            row.put("occupied", occupied);
            row.put("status", occupied ? "OCCUPIED" : "AVAILABLE");
        }
        return result;
    }

    private void annotateRack(Map<String, Object> row, RackReadModel rack) {
        row.put("rackName", rack.getName());
        row.put("rackKind", rack.getKind().name());
        row.put("rackStorageConditions",
                rack.getStorageConditions() != null ? rack.getStorageConditions().name() : null);
        row.put("rackMaxWeightKg", rack.getMaxWeightKg());
    }

    public List<Object> getCellsByRack(UUID rackId) {
        log.info("Getting cells for rack: {}", rackId);

        RackReadModel rack = rackRepository.findById(rackId)
                .orElseThrow(() -> AppException.notFound("Стеллаж не найден"));

        List<Map<String, Object>> enriched = new ArrayList<>();
        List<UUID> ids = new ArrayList<>();

        switch (rack.getKind()) {
            case SHELF:
                for (Shelf s : shelfRepository.findByRackId(rackId)) {
                    Map<String, Object> row = shelfToMap(s);
                    enriched.add(row);
                    ids.add(s.getShelfId());
                }
                break;
            case CELL:
                for (Cell c : cellRepository.findByRackId(rackId)) {
                    Map<String, Object> row = cellToMap(c);
                    enriched.add(row);
                    ids.add(c.getCellId());
                }
                break;
            case PALLET:
                for (PalletPlace p : palletPlaceRepository.findByRackId(rackId)) {
                    Map<String, Object> row = palletPlaceToMap(p);
                    enriched.add(row);
                    ids.add(p.getPlaceId());
                }
                break;
        }

        Map<UUID, ProductClient.CellLoad> loads = productClient.getCellsLoad(ids);
        for (Map<String, Object> row : enriched) {
            UUID id = (UUID) row.get("id");
            ProductClient.CellLoad load = loads.get(id);
            int itemsCount = load == null ? 0 : load.itemsCount();
            boolean occupied = load != null && load.occupied();
            row.put("itemsCount", itemsCount);
            row.put("totalQuantity", load == null ? java.math.BigDecimal.ZERO : load.totalQuantity());
            row.put("occupied", occupied);
            row.put("status", occupied ? "OCCUPIED" : "AVAILABLE");
        }

        log.info("Found {} cells for rack: {}", enriched.size(), rackId);
        return new ArrayList<>(enriched);
    }

    private Map<String, Object> shelfToMap(Shelf s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getShelfId());
        m.put("shelfId", s.getShelfId());
        m.put("rackId", s.getRackId());
        m.put("warehouseId", s.getWarehouseId());
        m.put("slotCode", s.getSlotCode());
        m.put("cellCode", s.getSlotCode());
        m.put("slotType", "SHELF");
        m.put("organizationId", s.getOrganizationId());
        m.put("shelfCapacityKg", s.getShelfCapacityKg());
        m.put("lengthCm", s.getLengthCm());
        m.put("widthCm", s.getWidthCm());
        m.put("heightCm", s.getHeightCm());
        m.put("remainingHeightCm",
                s.getRemainingHeightCm() != null ? s.getRemainingHeightCm() : s.getHeightCm());
        return m;
    }

    private Map<String, Object> cellToMap(Cell c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getCellId());
        m.put("cellId", c.getCellId());
        m.put("rackId", c.getRackId());
        m.put("warehouseId", c.getWarehouseId());
        m.put("slotCode", c.getSlotCode());
        m.put("cellCode", c.getSlotCode());
        m.put("slotType", "CELL");
        m.put("organizationId", c.getOrganizationId());
        m.put("maxWeightKg", c.getMaxWeightKg());
        m.put("lengthCm", c.getLengthCm());
        m.put("widthCm", c.getWidthCm());
        m.put("heightCm", c.getHeightCm());
        m.put("remainingHeightCm",
                c.getRemainingHeightCm() != null ? c.getRemainingHeightCm() : c.getHeightCm());
        return m;
    }

    private Map<String, Object> palletPlaceToMap(PalletPlace p) {
        java.math.BigDecimal capacity = p.getMaxHeightCm() != null ? p.getMaxHeightCm() : p.getHeightCm();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getPlaceId());
        m.put("placeId", p.getPlaceId());
        m.put("rackId", p.getRackId());
        m.put("warehouseId", p.getWarehouseId());
        m.put("slotCode", p.getSlotCode());
        m.put("cellCode", p.getSlotCode());
        m.put("slotType", "PALLET_PLACE");
        m.put("organizationId", p.getOrganizationId());
        m.put("lengthCm", p.getLengthCm());
        m.put("widthCm", p.getWidthCm());
        m.put("heightCm", p.getHeightCm());
        m.put("maxHeightCm", p.getMaxHeightCm());
        m.put("remainingHeightCm",
                p.getRemainingHeightCm() != null ? p.getRemainingHeightCm() : capacity);
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getSlotsByRack(UUID rackId) {
        RackReadModel rack = rackRepository.findById(rackId)
                .orElseThrow(() -> AppException.notFound("Стеллаж не найден"));

        Map<String, Object> result = new HashMap<>();
        result.put("rackId", rack.getRackId().toString());
        result.put("rackName", rack.getName());
        result.put("kind", rack.getKind().name());
        result.put("slots", getCellsByRack(rackId));
        return result;
    }

    @Transactional
    public void deleteRack(UUID rackId) {
        log.info("Deleting rack: {}", rackId);

        RackReadModel rack = rackRepository.findById(rackId)
                .orElseThrow(() -> AppException.notFound("Стеллаж не найден"));

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("rackId", rackId.toString());
        eventData.put("warehouseId", rack.getWarehouseId().toString());

        RackEvent rackEvent = RackEvent.builder()
                .rackId(rackId)
                .eventType("RACK_DELETED")
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(1)
                .createdAt(LocalDateTime.now())
                .build();
        eventRepository.save(rackEvent);

        rackRepository.delete(rack);

        log.info("Rack deleted successfully: {}", rackId);
    }

    private RackResponse mapToResponse(RackReadModel model) {
        return new RackResponse(
                model.getRackId(),
                model.getWarehouseId(),
                model.getKind(),
                model.getName(),
                model.getStorageConditions(),
                model.getMaxWeightKg(),
                model.getIsActive(),
                model.getCreatedAt(),
                model.getUpdatedAt()
        );
    }
}
