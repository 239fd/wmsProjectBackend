package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.TransferProductRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.model.enums.InventoryStatus;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Transactional
    public UUID receiveProduct(ReceiveProductRequest request) {
        return receiveProduct(request, null);
    }

    @Transactional
    public UUID receiveProduct(ReceiveProductRequest request, UUID organizationId) {
        try {
            log.info("Receiving product {} to warehouse {}, quantity: {} (org: {})",
                    request.productId(), request.warehouseId(), request.quantity(), organizationId);

            if (request.productId() == null) {
                throw AppException.badRequest("Product ID обязателен");
            }
            if (request.warehouseId() == null) {
                throw AppException.badRequest("Warehouse ID обязателен");
            }
            if (request.quantity() == null || request.quantity().compareTo(BigDecimal.ZERO) <= 0) {
                throw AppException.badRequest("Количество должно быть больше 0");
            }
            if (request.userId() == null) {
                throw AppException.badRequest("User ID обязателен");
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
                    .operationDate(LocalDateTime.now())
                    .notes(request.notes())
                    .build();
            operationRepository.save(operation);

        Optional<Inventory> existingInventory = inventoryRepository
                .findByProductIdAndWarehouseId(request.productId(), request.warehouseId());

        if (existingInventory.isPresent()) {

            Inventory inventory = existingInventory.get();
            BigDecimal qtyBefore = inventory.getQuantity();
            inventory.setQuantity(qtyBefore.add(request.quantity()));
            inventory.setCellId(request.cellId());
            inventory.setBatchId(request.batchId());
            if (inventory.getOrganizationId() == null) {
                inventory.setOrganizationId(effectiveOrgId);
            }
            inventoryRepository.save(inventory);
            inventoryEventService.recordQuantityChange(inventory, InventoryEventType.ITEM_ADDED,
                    qtyBefore, request.quantity(), operation.getOperationId(), request.userId(), null);
            log.info("Updated existing inventory: {}", inventory.getInventoryId());
        } else {

            Inventory inventory = Inventory.builder()
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
            inventoryEventService.recordQuantityChange(inventory, InventoryEventType.ITEM_ADDED,
                    BigDecimal.ZERO, request.quantity(), operation.getOperationId(), request.userId(), null);
            log.info("Created new inventory: {}", inventory.getInventoryId());
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
                throw AppException.badRequest("User ID обязателен");
            }

            ProductReadModel product = productRepository.findById(request.productId())
                    .orElseThrow(() -> AppException.notFound("Товар не найден"));

            UUID effectiveOrgId = organizationId != null ? organizationId : product.getOrganizationId();

            Inventory source = inventoryRepository
                    .findByProductIdAndWarehouseId(request.productId(), request.fromWarehouseId())
                    .orElseThrow(() -> AppException.notFound("Запасы товара на складе-отправителе не найдены"));

            if (organizationId != null && source.getOrganizationId() != null
                    && !organizationId.equals(source.getOrganizationId())) {
                throw AppException.forbidden("Запасы принадлежат другой организации");
            }

            BigDecimal available = source.getQuantity().subtract(source.getReservedQuantity());
            if (available.compareTo(request.quantity()) < 0) {
                throw AppException.badRequest(
                        String.format("Недостаточно товара для перемещения. Доступно: %s, запрошено: %s",
                                available, request.quantity()));
            }

            BigDecimal srcBefore = source.getQuantity();
            source.setQuantity(srcBefore.subtract(request.quantity()));
            source.setLastUpdated(LocalDateTime.now());
            inventoryRepository.save(source);

            Optional<Inventory> existingDest = (request.toCellId() != null)
                    ? inventoryRepository.findByCellId(request.toCellId())
                    : inventoryRepository.findByProductIdAndWarehouseId(request.productId(), request.toWarehouseId());

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
                dest.setUnitSku(null);
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
}
