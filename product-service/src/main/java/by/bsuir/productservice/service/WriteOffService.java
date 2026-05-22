package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.WriteOffRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryCount;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryCountRepository;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class WriteOffService {

    private final InventoryRepository inventoryRepository;
    private final ProductOperationRepository operationRepository;
    private final InventoryCountRepository countRepository;
    private final InventoryEventService inventoryEventService;
    private final ProductReadModelRepository productRepository;
    private final ProductBatchRepository batchRepository;
    private final FEFOService fefoService;

    @Transactional
    public Map<String, Object> writeOff(WriteOffRequest request, UUID organizationId) {
        log.info("Writing off product {} qty {} from warehouse {} (cell={}, batch={}, org={})",
                request.productId(), request.quantity(), request.warehouseId(),
                request.cellId(), request.batchId(), organizationId);

        List<Inventory> targets = new java.util.ArrayList<>();
        if (request.cellId() != null) {
            Inventory inv = inventoryRepository.findExactInventoryForUpdate(
                    request.productId(), request.batchId(),
                    request.warehouseId(), request.cellId())
                    .orElseThrow(() -> AppException.notFound(
                            "Запасы товара не найдены в указанной ячейке"));
            targets.add(inv);
        } else {
            var allocations = fefoService.selectInventory(
                    request.productId(), request.warehouseId(), request.quantity(),
                    by.bsuir.productservice.model.enums.AllocationStrategy.FEFO);
            for (var alloc : allocations) {
                Inventory inv = inventoryRepository.findByIdForUpdate(alloc.getInventoryId())
                        .orElseThrow(() -> AppException.notFound(
                                "Inventory не найден при списании: " + alloc.getInventoryId()));
                targets.add(inv);
            }
        }

        UUID effectiveOrgId = organizationId != null
                ? organizationId
                : targets.stream().map(Inventory::getOrganizationId)
                        .filter(java.util.Objects::nonNull).findFirst().orElse(null);

        BigDecimal totalAvailable = targets.stream()
                .map(inv -> inv.getQuantity().subtract(
                        inv.getReservedQuantity() != null ? inv.getReservedQuantity() : BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalAvailable.compareTo(request.quantity()) < 0) {
            throw AppException.badRequest(String.format(
                    "Недостаточно товара для списания. Доступно: %s, запрошено: %s",
                    totalAvailable, request.quantity()));
        }

        StringBuilder notes = new StringBuilder();
        notes.append("reason=").append(request.reason()).append("; ");
        if (request.basis() != null) notes.append("basis=").append(request.basis()).append("; ");
        if (request.responsibleUserId() != null) notes.append("responsible=").append(request.responsibleUserId()).append("; ");
        if (request.commissionMembers() != null && !request.commissionMembers().isEmpty()) {
            notes.append("commission=").append(request.commissionMembers().stream()
                    .map(UUID::toString).collect(Collectors.joining(","))).append("; ");
        }
        if (request.notes() != null) notes.append(request.notes());

        ProductOperation operation = ProductOperation.builder()
                .operationId(UUID.randomUUID())
                .operationType(OperationType.WRITE_OFF)
                .productId(request.productId())
                .batchId(request.batchId())
                .organizationId(effectiveOrgId)
                .warehouseId(request.warehouseId())
                .fromCellId(request.cellId())
                .quantity(request.quantity())
                .userId(request.userId())
                .operationDate(LocalDateTime.now())
                .notes(notes.toString())
                .build();
        operationRepository.save(operation);

        Map<UUID, BigDecimal> takenByInv = new java.util.LinkedHashMap<>();
        BigDecimal remaining = request.quantity();
        for (Inventory inv : targets) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
            BigDecimal avail = inv.getQuantity().subtract(
                    inv.getReservedQuantity() != null ? inv.getReservedQuantity() : BigDecimal.ZERO);
            BigDecimal take = remaining.min(avail);
            if (take.compareTo(BigDecimal.ZERO) <= 0) continue;

            takenByInv.put(inv.getInventoryId(), take);
            BigDecimal qtyBefore = inv.getQuantity();
            inv.setQuantity(qtyBefore.subtract(take));
            inv.setLastUpdated(LocalDateTime.now());
            inventoryRepository.save(inv);

            Map<String, Object> writeOffMeta = new HashMap<>();
            writeOffMeta.put("reason", request.reason());
            writeOffMeta.put("basis", request.basis());
            inventoryEventService.recordQuantityChange(inv, InventoryEventType.WRITTEN_OFF,
                    qtyBefore, take.negate(), operation.getOperationId(), request.userId(), writeOffMeta);

            if (inv.getQuantity().compareTo(BigDecimal.ZERO) <= 0
                    && (inv.getReservedQuantity() == null
                        || inv.getReservedQuantity().compareTo(BigDecimal.ZERO) <= 0)) {
                inventoryRepository.delete(inv);
                log.info("Inventory {} списан полностью → удалён (ячейка освобождена)", inv.getInventoryId());
            }

            remaining = remaining.subtract(take);
        }

        log.info("Write-off completed. Operation ID: {}, разнесено по {} inventory-строкам",
                operation.getOperationId(), targets.size());

        ProductReadModel product = productRepository.findById(request.productId()).orElse(null);
        BigDecimal productPriceFallback = product != null && product.getPrice() != null
                ? product.getPrice() : BigDecimal.ZERO;

        List<Map<String, Object>> itemRows = new java.util.ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        int idx = 1;
        for (Inventory inv : targets) {
            BigDecimal line = takenByInv.getOrDefault(inv.getInventoryId(), BigDecimal.ZERO);
            if (line.compareTo(BigDecimal.ZERO) <= 0) continue;

            ProductBatch b = inv.getBatchId() != null
                    ? batchRepository.findById(inv.getBatchId()).orElse(null) : null;
            BigDecimal unitPrice = b != null && b.getPurchasePrice() != null
                    ? b.getPurchasePrice() : productPriceFallback;
            BigDecimal lineTotal = unitPrice.multiply(line);
            grandTotal = grandTotal.add(lineTotal);

            Map<String, Object> item = new HashMap<>();
            item.put("lineNo", idx++);
            item.put("productId", request.productId().toString());
            item.put("productName", product != null ? product.getName() : null);
            item.put("name", product != null ? product.getName() : null);
            item.put("sku", product != null ? product.getSku() : null);
            item.put("productSku", product != null ? product.getSku() : null);
            item.put("unitOfMeasure", product != null && product.getUnitOfMeasure() != null
                    ? product.getUnitOfMeasure() : "шт");
            item.put("unit", product != null && product.getUnitOfMeasure() != null
                    ? product.getUnitOfMeasure() : "шт");
            item.put("batchId", inv.getBatchId() != null ? inv.getBatchId().toString() : null);
            item.put("batchNumber", b != null ? b.getBatchNumber() : null);
            item.put("expiryDate", b != null && b.getExpiryDate() != null
                    ? b.getExpiryDate().toString() : null);
            item.put("quantity", line.stripTrailingZeros().toPlainString());
            item.put("unitPrice", unitPrice);
            item.put("price", unitPrice);
            item.put("totalPrice", lineTotal);
            item.put("totalAmount", lineTotal);
            item.put("amount", lineTotal);
            item.put("cellId", inv.getCellId() != null ? inv.getCellId().toString() : null);
            item.put("reason", request.reason());
            itemRows.add(item);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("operationId", operation.getOperationId());
        result.put("productId", request.productId());
        result.put("quantity", request.quantity().stripTrailingZeros().toPlainString());
        result.put("reason", request.reason());
        result.put("basis", request.basis());
        result.put("items", itemRows);
        result.put("totalQuantity", request.quantity().stripTrailingZeros().toPlainString());
        result.put("totalAmount", grandTotal);
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMarkedItems(UUID warehouseId, UUID organizationId) {
        log.info("Fetching marked-for-writeoff items: warehouse={} (org={})", warehouseId, organizationId);

        if (organizationId == null) {
            return List.of();
        }

        List<InventoryCount> marked = (warehouseId != null)
                ? countRepository.findByOrganizationIdAndWarehouseIdAndMarkedForWriteoffTrue(organizationId, warehouseId)
                : countRepository.findByOrganizationIdAndMarkedForWriteoffTrue(organizationId);

        return marked.stream().map(this::toMarkedItemMap).collect(Collectors.toList());
    }

    public Page<Map<String, Object>> getMarkedItems(UUID warehouseId, UUID organizationId, Pageable pageable) {
        if (organizationId == null) {
            return Page.empty(pageable);
        }
        Page<InventoryCount> marked = (warehouseId != null)
                ? countRepository.findByOrganizationIdAndWarehouseIdAndMarkedForWriteoffTrue(organizationId, warehouseId, pageable)
                : countRepository.findByOrganizationIdAndMarkedForWriteoffTrue(organizationId, pageable);
        return marked.map(this::toMarkedItemMap);
    }

    private Map<String, Object> toMarkedItemMap(InventoryCount c) {
        Map<String, Object> m = new HashMap<>();
        m.put("countId", c.getCountId());
        m.put("sessionId", c.getSessionId());
        m.put("productId", c.getProductId());
        m.put("batchId", c.getBatchId());
        m.put("cellId", c.getCellId());
        m.put("warehouseId", c.getWarehouseId());
        m.put("expectedQuantity", c.getExpectedQuantity());
        m.put("actualQuantity", c.getActualQuantity());
        m.put("discrepancy", c.getDiscrepancy());
        m.put("notes", c.getNotes());
        return m;
    }
}
