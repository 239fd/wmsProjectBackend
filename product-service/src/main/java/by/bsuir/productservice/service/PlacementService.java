package by.bsuir.productservice.service;

import by.bsuir.productservice.client.WarehouseClient;
import by.bsuir.productservice.client.dto.CellInfoDto;
import by.bsuir.productservice.client.dto.RackInfoDto;
import by.bsuir.productservice.dto.request.PlacementRequest;
import by.bsuir.productservice.dto.response.PlacementResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.model.enums.StorageConditions;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlacementService {

    private final WarehouseClient warehouseClient;
    private final ProductBatchRepository batchRepository;
    private final ProductReadModelRepository productRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductOperationRepository operationRepository;

    @Transactional
    public PlacementResponse autoPlacement(PlacementRequest request, UUID organizationId, String userRole) {
        log.info("Auto placement: batch={} warehouse={} qty={} (org={})",
                request.batchId(), request.warehouseId(), request.quantity(), organizationId);

        ProductBatch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> AppException.notFound("Партия не найдена"));
        if (organizationId != null && batch.getOrganizationId() != null
                && !organizationId.equals(batch.getOrganizationId())) {
            throw AppException.forbidden("Партия принадлежит другой организации");
        }

        ProductReadModel product = productRepository.findById(batch.getProductId())
                .orElseThrow(() -> AppException.notFound("Товар не найден"));

        StorageConditions required = batch.getStorageConditions() != null
                ? batch.getStorageConditions()
                : StorageConditions.AMBIENT;

        List<RackInfoDto> matchingRacks = warehouseClient.getRacksByWarehouse(request.warehouseId(), userRole)
                .stream()
                .filter(r -> Boolean.TRUE.equals(r.isActive()))
                .filter(r -> matchesConditions(r.storageConditions(), required))
                .toList();

        if (matchingRacks.isEmpty()) {
            throw AppException.conflict(
                    "На складе нет стеллажей с условиями хранения " + required);
        }

        Set<UUID> occupiedCells = inventoryRepository.findByWarehouseId(request.warehouseId())
                .stream()
                .filter(inv -> inv.getQuantity() != null && inv.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .map(Inventory::getCellId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());

        record RackedCell(RackInfoDto rack, CellInfoDto cell, int rackIdx, int cellIdx) {}
        List<RackedCell> freeCells = new java.util.ArrayList<>();
        int rackIdx = 0;
        for (RackInfoDto rack : matchingRacks) {
            List<CellInfoDto> cells = warehouseClient.getCellsByRack(rack.rackId(), userRole);
            int cellIdx = 0;
            for (CellInfoDto cell : cells) {
                if (!occupiedCells.contains(cell.cellId())) {
                    freeCells.add(new RackedCell(rack, cell, rackIdx, cellIdx));
                }
                cellIdx++;
            }
            rackIdx++;
        }

        if (freeCells.isEmpty()) {
            throw AppException.conflict("На складе недостаточно места для размещения партии");
        }

        String abc = product.getAbcClass() != null ? product.getAbcClass() : "B";
        Comparator<RackedCell> sorter = switch (abc) {
            case "A" -> Comparator.comparingInt((RackedCell r) -> r.rackIdx).thenComparingInt(r -> r.cellIdx);
            case "C" -> Comparator.comparingInt((RackedCell r) -> -r.rackIdx).thenComparingInt(r -> -r.cellIdx);
            default -> Comparator.comparingInt((RackedCell r) -> Math.abs(r.rackIdx - matchingRacks.size() / 2));
        };
        freeCells.sort(sorter);

        RackedCell chosen = freeCells.get(0);
        log.info("Auto-selected cell {} (rack {}, ABC class {})",
                chosen.cell.cellId(), chosen.rack.name(), abc);

        return performPlacement(request, batch, organizationId, chosen.cell.cellId(),
                chosen.rack.rackId(), chosen.rack.name(), chosen.rack.storageConditions(), "AUTO");
    }

    @Transactional
    public PlacementResponse manualPlacement(PlacementRequest request, UUID organizationId, String userRole) {
        log.info("Manual placement: batch={} warehouse={} cell={} (org={})",
                request.batchId(), request.warehouseId(), request.cellId(), organizationId);

        if (request.cellId() == null) {
            throw AppException.badRequest("Cell ID обязателен для ручного размещения");
        }

        ProductBatch batch = batchRepository.findById(request.batchId())
                .orElseThrow(() -> AppException.notFound("Партия не найдена"));
        if (organizationId != null && batch.getOrganizationId() != null
                && !organizationId.equals(batch.getOrganizationId())) {
            throw AppException.forbidden("Партия принадлежит другой организации");
        }

        StorageConditions required = batch.getStorageConditions() != null
                ? batch.getStorageConditions()
                : StorageConditions.AMBIENT;

        List<RackInfoDto> racks = warehouseClient.getRacksByWarehouse(request.warehouseId(), userRole);
        RackInfoDto matchingRack = null;
        for (RackInfoDto rack : racks) {
            List<CellInfoDto> cells = warehouseClient.getCellsByRack(rack.rackId(), userRole);
            if (cells.stream().anyMatch(c -> request.cellId().equals(c.cellId()))) {
                matchingRack = rack;
                break;
            }
        }
        if (matchingRack == null) {
            throw AppException.notFound("Ячейка не найдена на указанном складе");
        }

        if (!matchesConditions(matchingRack.storageConditions(), required)) {
            throw AppException.conflict(
                    "Условия хранения ячейки (" + matchingRack.storageConditions()
                            + ") не соответствуют требуемым (" + required + ")");
        }

        boolean cellOccupied = inventoryRepository.findByCellId(request.cellId())
                .filter(inv -> inv.getQuantity() != null && inv.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                .isPresent();
        if (cellOccupied) {
            throw AppException.conflict("Ячейка уже занята");
        }

        return performPlacement(request, batch, organizationId, request.cellId(),
                matchingRack.rackId(), matchingRack.name(), matchingRack.storageConditions(), "MANUAL");
    }

    private PlacementResponse performPlacement(PlacementRequest request, ProductBatch batch,
                                               UUID organizationId, UUID cellId, UUID rackId,
                                               String rackName, String storageConditions, String mode) {
        UUID effectiveOrgId = organizationId != null ? organizationId : batch.getOrganizationId();

        Inventory inventory = Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .productId(batch.getProductId())
                .batchId(batch.getBatchId())
                .organizationId(effectiveOrgId)
                .warehouseId(request.warehouseId())
                .cellId(cellId)
                .quantity(request.quantity())
                .reservedQuantity(BigDecimal.ZERO)
                .status(InventoryStatus.AVAILABLE)
                .lastUpdated(LocalDateTime.now())
                .build();
        inventoryRepository.save(inventory);

        ProductOperation operation = ProductOperation.builder()
                .operationId(UUID.randomUUID())
                .operationType(OperationType.STAGING)
                .productId(batch.getProductId())
                .batchId(batch.getBatchId())
                .organizationId(effectiveOrgId)
                .warehouseId(request.warehouseId())
                .toCellId(cellId)
                .quantity(request.quantity())
                .userId(request.userId())
                .operationDate(LocalDateTime.now())
                .notes("Размещение " + mode + ". " + (request.notes() != null ? request.notes() : ""))
                .build();
        operationRepository.save(operation);

        return new PlacementResponse(
                operation.getOperationId(),
                inventory.getInventoryId(),
                cellId,
                rackId,
                rackName,
                storageConditions,
                mode);
    }

    private boolean matchesConditions(String rackConditions, StorageConditions required) {
        if (rackConditions == null) {
            return required == StorageConditions.AMBIENT || required == StorageConditions.DRY;
        }
        try {
            return StorageConditions.valueOf(rackConditions) == required;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
