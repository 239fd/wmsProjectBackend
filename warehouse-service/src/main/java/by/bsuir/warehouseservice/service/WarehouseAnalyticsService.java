package by.bsuir.warehouseservice.service;

import by.bsuir.warehouseservice.model.entity.WarehouseReadModel;
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

        Map<String, Object> structure = new HashMap<>();
        structure.put("hasRacks", true);
        structure.put("racksCount", 0);
        analytics.put("structure", structure);

        return analytics;
    }

    @Cacheable(value = "orgWarehousesSummary", key = "#orgId")
    public Map<String, Object> getOrganizationWarehousesSummary(UUID orgId) {
        log.info("Calculating warehouses summary for organization: {}", orgId);

        List<WarehouseReadModel> warehouses = warehouseRepository.findByOrgId(orgId);

        long activeWarehouses = warehouses.stream()
                .filter(WarehouseReadModel::getIsActive)
                .count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("orgId", orgId);
        summary.put("totalWarehouses", warehouses.size());
        summary.put("activeWarehouses", activeWarehouses);
        summary.put("warehouses", warehouses.stream()
                .map(w -> Map.of(
                        "warehouseId", w.getWarehouseId(),
                        "name", w.getName(),
                        "address", w.getAddress() != null ? w.getAddress() : "",
                        "isActive", w.getIsActive(),
                        "responsibleUserId", w.getResponsibleUserId() != null ? w.getResponsibleUserId() : ""
                ))
                .collect(Collectors.toList()));

        return summary;
    }
}

