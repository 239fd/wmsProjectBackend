package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.WriteOffRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryCount;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryCountRepository;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
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

    @Transactional
    public Map<String, Object> writeOff(WriteOffRequest request, UUID organizationId) {
        log.info("Writing off product {} qty {} from warehouse {} (org: {})",
                request.productId(), request.quantity(), request.warehouseId(), organizationId);

        Inventory inventory = inventoryRepository
                .findByProductIdAndWarehouseIdForUpdate(request.productId(), request.warehouseId())
                .orElseThrow(() -> AppException.notFound("Запасы товара на складе не найдены"));

        if (organizationId != null && inventory.getOrganizationId() != null
                && !organizationId.equals(inventory.getOrganizationId())) {
            throw AppException.forbidden("Запасы принадлежат другой организации");
        }

        BigDecimal available = inventory.getQuantity().subtract(inventory.getReservedQuantity());
        if (available.compareTo(request.quantity()) < 0) {
            throw AppException.badRequest(
                    String.format("Недостаточно товара для списания. Доступно: %s, запрошено: %s",
                            available, request.quantity()));
        }

        UUID effectiveOrgId = organizationId != null ? organizationId : inventory.getOrganizationId();

        BigDecimal qtyBefore = inventory.getQuantity();
        inventory.setQuantity(qtyBefore.subtract(request.quantity()));
        inventory.setLastUpdated(LocalDateTime.now());
        inventoryRepository.save(inventory);

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

        Map<String, Object> writeOffMeta = new HashMap<>();
        writeOffMeta.put("reason", request.reason());
        writeOffMeta.put("basis", request.basis());
        inventoryEventService.recordQuantityChange(inventory, InventoryEventType.WRITTEN_OFF,
                qtyBefore, request.quantity().negate(), operation.getOperationId(), request.userId(), writeOffMeta);

        log.info("Write-off completed. Operation ID: {}", operation.getOperationId());

        Map<String, Object> result = new HashMap<>();
        result.put("operationId", operation.getOperationId());
        result.put("productId", request.productId());
        result.put("quantity", request.quantity());
        result.put("reason", request.reason());
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
