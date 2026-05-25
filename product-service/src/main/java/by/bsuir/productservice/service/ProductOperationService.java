package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.TransferProductRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.model.enums.OperationStatus;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.model.entity.ProductBatch;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductOperationService {

    private final ProductOperationRepository operationRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductReadModelRepository productRepository;
    private final by.bsuir.productservice.client.WarehouseClient warehouseClient;
    private final InventoryEventService inventoryEventService;
    private final ProductBatchRepository batchRepository;
    private final DocumentRegistryService documentRegistryService;
    private final PlacementService placementService;

    @Transactional
    public UUID receiveProduct(ReceiveProductRequest request) {
        return receiveProduct(request, null);
    }

    @Transactional
    public UUID receiveProduct(ReceiveProductRequest request, UUID organizationId) {
        return doReceive(request, organizationId, null);
    }

    @Transactional
    public UUID receiveItemInSession(ReceiveProductRequest request, UUID organizationId, UUID sessionId) {
        return doReceive(request, organizationId, sessionId);
    }

    private UUID doReceive(ReceiveProductRequest request, UUID organizationId, UUID sessionId) {
        try {
            log.info("Receiving product {} to warehouse {}, quantity: {} (org: {}, session: {})",
                    request.productId(), request.warehouseId(), request.quantity(), organizationId, sessionId);

            if (request.productId() == null) {
                throw AppException.badRequest("Товар обязателен");
            }
            if (request.warehouseId() == null) {
                throw AppException.badRequest("Склад обязателен");
            }
            if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw AppException.badRequest("Количество должно быть больше 0");
            }
            if (request.userId() == null) {
                throw AppException.badRequest("Пользователь обязателен");
            }

            ProductReadModel product = productRepository.findById(request.productId())
                    .orElseThrow(() -> AppException.notFound("Товар не найден"));

            UUID effectiveOrgId = organizationId != null ? organizationId : product.getOrganizationId();

            ensureWarehouseCanFitProduct(request.warehouseId(), request.cellId());

            ProductOperation operation = ProductOperation.builder()
                    .operationId(UUID.randomUUID())
                    .operationType(OperationType.RECEIPT)
                    .productId(request.productId())
                    .batchId(request.batchId())
                    .organizationId(effectiveOrgId)
                    .warehouseId(request.warehouseId())
                    .toCellId(request.cellId())
                    .quantity(request.quantity())
                    .userId(request.userId())
                    .sessionId(sessionId)
                    .status(OperationStatus.PAUSED)
                    .operationDate(LocalDateTime.now())
                    .notes(request.notes())
                    .build();
            operationRepository.save(operation);

            Inventory existing = inventoryRepository.findExactInventoryForUpdate(
                    request.productId(), request.batchId(),
                    request.warehouseId(), request.cellId()).orElse(null);
            Inventory inventory;
            BigDecimal qtyBefore;
            if (existing != null) {
                qtyBefore = existing.getQuantity() != null ? existing.getQuantity() : BigDecimal.ZERO;
                existing.setQuantity(qtyBefore.add(request.quantity()));
                existing.setLastUpdated(LocalDateTime.now());
                inventory = inventoryRepository.save(existing);
                log.info("Merged into existing inventory: {} (+{})",
                        inventory.getInventoryId(), request.quantity());
            } else {
                qtyBefore = BigDecimal.ZERO;
                inventory = Inventory.builder()
                        .inventoryId(UUID.randomUUID())
                        .productId(request.productId())
                        .batchId(request.batchId())
                        .organizationId(effectiveOrgId)
                        .warehouseId(request.warehouseId())
                        .cellId(request.cellId())
                        .quantity(request.quantity())
                        .reservedQuantity(BigDecimal.ZERO)
                        .status(InventoryStatus.AVAILABLE)
                        .lastUpdated(LocalDateTime.now())
                        .build();
                inventoryRepository.save(inventory);
                log.info("Created new inventory: {}", inventory.getInventoryId());
            }
            inventoryEventService.recordQuantityChange(inventory, InventoryEventType.ITEM_ADDED,
                    qtyBefore, request.quantity(), operation.getOperationId(), request.userId(), null);

            if (request.cellId() != null && request.batchId() != null) {
                BigDecimal heightDelta = computeHeightDelta(request.batchId(), request.quantity());
                if (heightDelta.signum() > 0) {
                    warehouseClient.adjustSlotHeight(request.cellId(), heightDelta.negate());
                }
            }

            log.info("Product received successfully. Operation ID: {}", operation.getOperationId());
            return operation.getOperationId();

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error receiving product: {}", e.getMessage(), e);
            throw AppException.internalError("Ошибка при приёмке товара: " + e.getMessage());
        }
    }

    private void ensureWarehouseCanFitProduct(UUID warehouseId, UUID cellId) {
        if (cellId != null) {
            boolean occupied = inventoryRepository.findByCellId(cellId)
                    .filter(inv -> inv.getQuantity() != null
                            && inv.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                    .isPresent();
            if (occupied) {
                throw AppException.conflict("Целевая ячейка занята — приёмка отменена");
            }
            return;
        }
        try {
            var racks = warehouseClient.getRacksByWarehouse(warehouseId, "WORKER");
            if (racks == null || racks.isEmpty()) {
                throw AppException.conflict("На складе нет стеллажей — приёмка отменена");
            }
            java.util.Set<UUID> occupied = inventoryRepository.findByWarehouseId(warehouseId).stream()
                    .filter(inv -> inv.getQuantity() != null
                            && inv.getQuantity().compareTo(BigDecimal.ZERO) > 0)
                    .map(Inventory::getCellId)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.toSet());
            boolean anyFreeCell = racks.stream()
                    .filter(r -> Boolean.TRUE.equals(r.isActive()))
                    .flatMap(r -> warehouseClient.getCellsByRack(r.rackId(), "WORKER").stream())
                    .anyMatch(c -> !occupied.contains(c.cellId()));
            if (!anyFreeCell) {
                throw AppException.conflict("На складе нет свободных ячеек — приёмка отменена");
            }
        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.warn("Не удалось проверить вместимость склада {}: {}", warehouseId, e.getMessage());
        }
    }

    @Transactional
    public UUID transferProduct(TransferProductRequest request, UUID organizationId) {
        try {
            log.info("Transferring product {} from wh={}/cell={} to wh={}/cell={}, qty={} (org={})",
                    request.productId(), request.fromWarehouseId(), request.fromCellId(),
                    request.toWarehouseId(), request.toCellId(), request.quantity(), organizationId);

            if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw AppException.badRequest("Количество должно быть больше 0");
            }
            if (request.userId() == null) {
                throw AppException.badRequest("Пользователь обязателен");
            }

            ProductReadModel product = productRepository.findById(request.productId())
                    .orElseThrow(() -> AppException.notFound("Товар не найден"));

            UUID effectiveOrgId = organizationId != null ? organizationId : product.getOrganizationId();

            Inventory source = inventoryRepository
                    .findExactInventoryForUpdate(
                            request.productId(), request.batchId(),
                            request.fromWarehouseId(), request.fromCellId())
                    .orElseThrow(() -> AppException.notFound(
                            "Запасы товара в указанной ячейке не найдены"));

            if (organizationId != null && source.getOrganizationId() != null
                    && !organizationId.equals(source.getOrganizationId())) {
                throw AppException.forbidden("Запасы принадлежат другой организации");
            }

            UUID validateBatchId = request.batchId() != null ? request.batchId() : source.getBatchId();
            ProductBatch destBatch = validateBatchId != null
                    ? batchRepository.findById(validateBatchId).orElse(null) : null;
            placementService.validateTransferFit(
                    request.toWarehouseId(), request.toCellId(), destBatch, product,
                    request.quantity(), "WORKER");

            BigDecimal available = source.getQuantity().subtract(source.getReservedQuantity());
            if (available.compareTo(request.quantity()) < 0) {
                throw AppException.badRequest(
                        String.format("Недостаточно товара для перемещения. Доступно: %s, запрошено: %s",
                                available, request.quantity()));
            }

            BigDecimal srcBefore = source.getQuantity();
            BigDecimal srcAfter = srcBefore.subtract(request.quantity());
            if (srcAfter.compareTo(BigDecimal.ZERO) < 0) {
                throw AppException.badRequest(String.format(
                        "Недостаточно товара для перемещения. В ячейке: %s, запрошено: %s",
                        srcBefore, request.quantity()));
            }
            source.setQuantity(srcAfter);
            source.setLastUpdated(LocalDateTime.now());
            inventoryRepository.save(source);

            UUID destBatchId = request.batchId() != null ? request.batchId() : source.getBatchId();
            Optional<Inventory> existingDest = inventoryRepository.findExactInventoryForUpdate(
                    request.productId(), destBatchId,
                    request.toWarehouseId(), request.toCellId());

            BigDecimal dstBefore;
            Inventory dest;
            if (existingDest.isPresent()) {
                dest = existingDest.get();
                dstBefore = dest.getQuantity();
                dest.setQuantity(dstBefore.add(request.quantity()));
                dest.setLastUpdated(LocalDateTime.now());
                if (dest.getOrganizationId() == null) {
                    dest.setOrganizationId(effectiveOrgId);
                }
            } else {
                dstBefore = BigDecimal.ZERO;
                dest = Inventory.builder()
                        .inventoryId(UUID.randomUUID())
                        .productId(request.productId())
                        .batchId(request.batchId() != null ? request.batchId() : source.getBatchId())
                        .organizationId(effectiveOrgId)
                        .warehouseId(request.toWarehouseId())
                        .cellId(request.toCellId())
                        .quantity(request.quantity())
                        .reservedQuantity(BigDecimal.ZERO)
                        .status(InventoryStatus.AVAILABLE)
                        .lastUpdated(LocalDateTime.now())
                        .build();
            }
            inventoryRepository.save(dest);

            ProductOperation operation = ProductOperation.builder()
                    .operationId(UUID.randomUUID())
                    .operationType(OperationType.TRANSFER)
                    .productId(request.productId())
                    .batchId(request.batchId() != null ? request.batchId() : source.getBatchId())
                    .organizationId(effectiveOrgId)
                    .warehouseId(request.toWarehouseId())
                    .fromCellId(request.fromCellId())
                    .toCellId(request.toCellId())
                    .quantity(request.quantity())
                    .userId(request.userId())
                    .operationDate(LocalDateTime.now())
                    .notes(request.notes())
                    .build();
            operationRepository.save(operation);

            Map<String, Object> transferMeta = Map.of(
                    "fromWarehouseId", request.fromWarehouseId(),
                    "toWarehouseId", request.toWarehouseId());
            inventoryEventService.recordQuantityChange(source, InventoryEventType.ITEM_REMOVED,
                    srcBefore, request.quantity().negate(), operation.getOperationId(), request.userId(), transferMeta);
            inventoryEventService.recordQuantityChange(dest, InventoryEventType.ITEM_ADDED,
                    dstBefore, request.quantity(), operation.getOperationId(), request.userId(), transferMeta);

            if (source.getQuantity().compareTo(BigDecimal.ZERO) == 0
                    && (source.getReservedQuantity() == null
                        || source.getReservedQuantity().compareTo(BigDecimal.ZERO) == 0)) {
                inventoryRepository.delete(source);
                log.info("Source inventory {} drained → deleted (cell freed)", source.getInventoryId());
            }

            UUID batchForHeight = request.batchId() != null ? request.batchId() : source.getBatchId();
            if (batchForHeight != null) {
                BigDecimal heightDelta = computeHeightDelta(batchForHeight, request.quantity());
                if (heightDelta.signum() > 0) {
                    if (request.fromCellId() != null) {
                        warehouseClient.adjustSlotHeight(request.fromCellId(), heightDelta);
                    }
                    if (request.toCellId() != null) {
                        warehouseClient.adjustSlotHeight(request.toCellId(), heightDelta.negate());
                    }
                }
            }

            generateTransferPlacementList(operation, product, request, effectiveOrgId);

            log.info("Product transferred successfully. Operation ID: {}, dest inventory: {}",
                    operation.getOperationId(), dest.getInventoryId());
            return operation.getOperationId();

        } catch (AppException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error transferring product: {}", e.getMessage(), e);
            throw AppException.internalError("Ошибка при перемещении товара: " + e.getMessage());
        }
    }

    private void generateTransferPlacementList(
            ProductOperation operation, ProductReadModel product,
            TransferProductRequest request, UUID organizationId) {
        try {
            ProductBatch batch = operation.getBatchId() != null
                    ? batchRepository.findById(operation.getBatchId()).orElse(null) : null;
            Map<String, String> loc = resolveCellLocation(request.toCellId());

            Map<String, Object> line = new HashMap<>();
            line.put("rowNumber", 1);
            line.put("lineNo", 1);
            line.put("quantity", request.quantity() != null
                    ? request.quantity().stripTrailingZeros().toPlainString() : "0");
            line.put("batchNumber", batch != null ? batch.getBatchNumber() : null);
            line.put("rackName", loc.get("rackName"));
            line.put("cellCode", loc.get("cellCode"));
            line.put("cellId", request.toCellId() != null ? request.toCellId().toString() : null);
            line.put("storageConditions", batch != null && batch.getStorageConditions() != null
                    ? batch.getStorageConditions().name() : null);
            if (product != null) {
                line.put("productName", product.getName());
                line.put("name", product.getName());
                line.put("sku", product.getSku());
                line.put("unit", product.getUnitOfMeasure() != null ? product.getUnitOfMeasure() : "шт");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("warehouseId", request.toWarehouseId() != null
                    ? request.toWarehouseId().toString() : null);
            payload.put("date", java.time.LocalDate.now().toString());
            payload.put("documentDate", java.time.LocalDate.now().toString());
            payload.put("items", java.util.List.of(line));

            documentRegistryService.register(
                    operation.getOperationId(), "placement-list", payload,
                    organizationId, request.userId());
        } catch (Exception ex) {
            log.warn("Не удалось сгенерировать лист размещения для перемещения {}: {}",
                    operation.getOperationId(), ex.getMessage());
        }
    }

    private Map<String, String> resolveCellLocation(UUID cellId) {
        String rackName = "—";
        String cellCode = cellId != null
                ? String.valueOf(cellId).substring(0, 8).toUpperCase() : "—";
        if (cellId == null) {
            return Map.of("rackName", rackName, "cellCode", cellCode);
        }
        try {
            Map<String, Object> info = warehouseClient.getCellInfo(cellId, "WORKER");
            if (info != null) {
                if (info.get("slotCode") != null) {
                    cellCode = info.get("slotCode").toString();
                }
                if (info.get("rackId") != null) {
                    UUID rackId = UUID.fromString(info.get("rackId").toString());
                    var rack = warehouseClient.getRack(rackId, "WORKER");
                    if (rack != null && rack.name() != null) {
                        rackName = rack.name();
                    }
                }
            }
        } catch (Exception ex) {
            log.debug("resolveCellLocation: failed for cell {}: {}", cellId, ex.getMessage());
        }
        return Map.of("rackName", rackName, "cellCode", cellCode);
    }

    BigDecimal computeHeightDelta(UUID batchId, BigDecimal quantityUnits) {
        if (batchId == null || quantityUnits == null || quantityUnits.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        ProductBatch batch = batchRepository.findById(batchId).orElse(null);
        if (batch == null || batch.getPackageHeightCm() == null) return BigDecimal.ZERO;
        int upp = (batch.getUnitsPerPackage() != null && batch.getUnitsPerPackage() > 0)
                ? batch.getUnitsPerPackage() : 1;
        BigDecimal numPackages = quantityUnits.divide(
                BigDecimal.valueOf(upp), 0, java.math.RoundingMode.CEILING);
        return batch.getPackageHeightCm().multiply(numPackages);
    }
}
