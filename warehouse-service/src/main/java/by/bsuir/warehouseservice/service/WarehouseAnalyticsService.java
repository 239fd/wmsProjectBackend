package by.bsuir.warehouseservice.service;

import by.bsuir.warehouseservice.client.ProductClient;
import by.bsuir.warehouseservice.model.entity.Cell;
import by.bsuir.warehouseservice.model.entity.PalletPlace;
import by.bsuir.warehouseservice.model.entity.RackReadModel;
import by.bsuir.warehouseservice.model.entity.Shelf;
import by.bsuir.warehouseservice.model.entity.WarehouseReadModel;
import by.bsuir.warehouseservice.model.enums.RackKind;
import by.bsuir.warehouseservice.model.enums.StorageConditions;
import by.bsuir.warehouseservice.repository.CellRepository;
import by.bsuir.warehouseservice.repository.PalletPlaceRepository;
import by.bsuir.warehouseservice.repository.RackReadModelRepository;
import by.bsuir.warehouseservice.repository.ShelfRepository;
import by.bsuir.warehouseservice.repository.WarehouseReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WarehouseAnalyticsService {

    private final WarehouseReadModelRepository warehouseRepository;
    private final RackReadModelRepository rackRepository;
    private final ShelfRepository shelfRepository;
    private final CellRepository cellRepository;
    private final PalletPlaceRepository palletPlaceRepository;
    private final ProductClient productClient;

    @Cacheable(value = "warehouseAnalytics", key = "#warehouseId")
    public Map<String, Object> getWarehouseAnalytics(UUID warehouseId) {
        log.info("Calculating analytics for warehouse: {}", warehouseId);

        WarehouseReadModel warehouse = warehouseRepository.findByWarehouseId(warehouseId)
                .orElseThrow(() -> new RuntimeException("Warehouse not found"));

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("warehouseId", warehouse.getWarehouseId());
        analytics.put("name", warehouse.getName());
        analytics.put("address", warehouse.getAddress());
        analytics.put("orgId", warehouse.getOrgId());
        analytics.put("responsibleUserId", warehouse.getResponsibleUserId());
        analytics.put("isActive", warehouse.getIsActive());
        analytics.put("createdAt", warehouse.getCreatedAt());
        analytics.put("updatedAt", warehouse.getUpdatedAt());
        analytics.put("structure", buildStructure(warehouseId));

        return analytics;
    }

    @Cacheable(value = "orgWarehousesSummary", key = "#orgId")
    public Map<String, Object> getOrganizationWarehousesSummary(UUID orgId) {
        log.info("Calculating warehouses summary for organization: {}", orgId);

        List<WarehouseReadModel> warehouses = warehouseRepository.findByOrgId(orgId);
        long activeWarehouses = warehouses.stream().filter(WarehouseReadModel::getIsActive).count();

        List<Map<String, Object>> warehousesWithStructure = warehouses.stream()
                .map(w -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("warehouseId", w.getWarehouseId());
                    m.put("name", w.getName());
                    m.put("address", w.getAddress() != null ? w.getAddress() : "");
                    m.put("isActive", w.getIsActive());
                    m.put("responsibleUserId", w.getResponsibleUserId() != null ? w.getResponsibleUserId() : "");
                    m.put("structure", buildStructure(w.getWarehouseId()));
                    return m;
                })
                .collect(Collectors.toList());

        Map<String, Object> summary = new HashMap<>();
        summary.put("orgId", orgId);
        summary.put("totalWarehouses", warehouses.size());
        summary.put("activeWarehouses", activeWarehouses);
        summary.put("warehouses", warehousesWithStructure);
        return summary;
    }

    private Map<String, Object> buildStructure(UUID warehouseId) {
        List<RackReadModel> racks = rackRepository.findByWarehouseId(warehouseId);

        EnumMap<RackKind, Integer> racksByKind = new EnumMap<>(RackKind.class);
        EnumMap<StorageConditions, Integer> racksByConditions = new EnumMap<>(StorageConditions.class);
        for (RackKind k : RackKind.values()) racksByKind.put(k, 0);
        for (StorageConditions c : StorageConditions.values()) racksByConditions.put(c, 0);

        List<UUID> allSlotIds = new ArrayList<>();
        int totalSlots = 0;

        for (RackReadModel rack : racks) {
            racksByKind.merge(rack.getKind(), 1, Integer::sum);
            if (rack.getStorageConditions() != null) {
                racksByConditions.merge(rack.getStorageConditions(), 1, Integer::sum);
            }
            switch (rack.getKind()) {
                case SHELF -> {
                    List<Shelf> shelves = shelfRepository.findByRackId(rack.getRackId());
                    totalSlots += shelves.size();
                    shelves.forEach(s -> allSlotIds.add(s.getShelfId()));
                }
                case CELL -> {
                    List<Cell> cells = cellRepository.findByRackId(rack.getRackId());
                    totalSlots += cells.size();
                    cells.forEach(c -> allSlotIds.add(c.getCellId()));
                }
                case PALLET -> {
                    List<PalletPlace> places = palletPlaceRepository.findByRackId(rack.getRackId());
                    totalSlots += places.size();
                    places.forEach(p -> allSlotIds.add(p.getPlaceId()));
                }
            }
        }

        Map<UUID, ProductClient.CellLoad> loads = productClient.getCellsLoad(allSlotIds);
        long occupiedSlots = loads.values().stream().filter(ProductClient.CellLoad::occupied).count();
        double utilizationPercent = totalSlots > 0
                ? Math.round(((double) occupiedSlots / totalSlots) * 1000.0) / 10.0
                : 0.0;

        Map<String, Object> structure = new HashMap<>();
        structure.put("racksCount", racks.size());
        structure.put("racksByKind", racksByKind);
        structure.put("racksByStorageConditions", racksByConditions);
        structure.put("totalSlots", totalSlots);
        structure.put("occupiedSlots", occupiedSlots);
        structure.put("freeSlots", totalSlots - occupiedSlots);
        structure.put("utilizationPercent", utilizationPercent);
        return structure;
    }
}
