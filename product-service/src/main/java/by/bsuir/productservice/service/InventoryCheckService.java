package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.StartInventoryRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryCount;
import by.bsuir.productservice.model.entity.InventorySession;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.repository.InventoryCountRepository;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.InventorySessionRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryCheckService {

    private final InventorySessionRepository sessionRepository;
    private final InventoryCountRepository countRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductOperationRepository operationRepository;
    private final ProductReadModelRepository productReadModelRepository;
    private final ProductBatchRepository productBatchRepository;
    private final ObjectMapper objectMapper;
    private final InventoryEventService inventoryEventService;
    private final DocumentRegistryService documentRegistryService;
    private final by.bsuir.productservice.client.WarehouseClient warehouseClient;

    @Transactional
    public UUID startInventory(UUID warehouseId, UUID userId, String notes) {
        return startInventoryInternal(warehouseId, userId, null, null, null, null, notes);
    }

    @Transactional
    public UUID startInventory(UUID warehouseId, UUID userId, UUID organizationId, String notes) {
        return startInventoryInternal(warehouseId, userId, organizationId, null, null, null, notes);
    }

    @Transactional
    public UUID startInventory(StartInventoryRequest request, UUID organizationId) {
        return startInventoryInternal(
                request.warehouseId(),
                request.userId(),
                organizationId,
                request.responsibleUserId(),
                request.reason(),
                request.commissionMembers(),
                request.notes());
    }

    private UUID startInventoryInternal(UUID warehouseId, UUID userId, UUID organizationId,
                                        UUID responsibleUserId, String reason,
                                        List<UUID> commissionMembers, String notes) {
        log.info("Starting inventory check for warehouse: {} (org: {})", warehouseId, organizationId);

        List<InventorySession> activeSessions = sessionRepository.findByStatus(
                InventorySession.SessionStatus.IN_PROGRESS);

        for (InventorySession session : activeSessions) {
            if (session.getWarehouseId().equals(warehouseId)) {
                throw AppException.conflict("На складе уже идёт инвентаризация. Сессия: "
                        + session.getSessionId());
            }
        }

        String commissionJson = null;
        if (commissionMembers != null && !commissionMembers.isEmpty()) {
            try {
                commissionJson = objectMapper.writeValueAsString(commissionMembers);
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize commission members: {}", e.getMessage());
            }
        }

        UUID sessionId = UUID.randomUUID();
        InventorySession session = InventorySession.builder()
                .sessionId(sessionId)
                .organizationId(organizationId)
                .warehouseId(warehouseId)
                .startedBy(userId)
                .responsibleUserId(responsibleUserId)
                .reason(reason)
                .commissionMembers(commissionJson)
                .startedAt(LocalDateTime.now())
                .status(InventorySession.SessionStatus.IN_PROGRESS)
                .notes(notes)
                .build();
        sessionRepository.save(session);

        List<Inventory> currentInventory = (organizationId != null)
                ? inventoryRepository.findByOrganizationIdAndWarehouseId(organizationId, warehouseId)
                : inventoryRepository.findByWarehouseId(warehouseId);

        log.info("Creating snapshot of {} inventory records", currentInventory.size());

        for (Inventory inv : currentInventory) {
            InventoryCount count = InventoryCount.builder()
                    .countId(UUID.randomUUID())
                    .sessionId(sessionId)
                    .organizationId(organizationId != null ? organizationId : inv.getOrganizationId())
                    .productId(inv.getProductId())
                    .batchId(inv.getBatchId())
                    .cellId(inv.getCellId())
                    .warehouseId(warehouseId)
                    .expectedQuantity(inv.getQuantity())
                    .actualQuantity(null)
                    .discrepancy(BigDecimal.ZERO)
                    .markedForWriteoff(false)
                    .build();
            countRepository.save(count);
        }

        log.info("Inventory session started: {}", sessionId);
        return sessionId;
    }

    @Transactional
    public void recordActualCount(UUID sessionId, UUID productId, UUID cellId,
                                   BigDecimal actualQuantity, String notes) {
        log.info("Recording actual count for session: {}, product: {}, cell: {}, quantity: {}",
                sessionId, productId, cellId, actualQuantity);

        InventorySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("Сессия инвентаризации не найдена"));

        if (session.getStatus() != InventorySession.SessionStatus.IN_PROGRESS) {
            throw AppException.badRequest("Сессия инвентаризации уже завершена");
        }

        List<InventoryCount> matches = countRepository.findBySessionId(sessionId).stream()
                .filter(c -> c.getProductId().equals(productId)
                        && (cellId == null || cellId.equals(c.getCellId())))
                .collect(Collectors.toList());
        if (matches.isEmpty()) {
            throw AppException.notFound("Запись подсчёта не найдена");
        }
        if (matches.size() > 1) {
            throw AppException.conflict(
                    "Несколько партий товара в этой ячейке — укажите countId конкретной строки");
        }

        applyActualCount(matches.get(0), actualQuantity, notes);
    }

    @Transactional
    public void recordActualCountById(UUID sessionId, UUID countId,
                                      BigDecimal actualQuantity, String notes) {
        log.info("Recording actual count by countId={} for session: {}, quantity: {}",
                countId, sessionId, actualQuantity);

        InventorySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("Сессия инвентаризации не найдена"));
        if (session.getStatus() != InventorySession.SessionStatus.IN_PROGRESS) {
            throw AppException.badRequest("Сессия инвентаризации уже завершена");
        }

        InventoryCount targetCount = countRepository.findById(countId)
                .orElseThrow(() -> AppException.notFound("Запись подсчёта не найдена"));
        if (!sessionId.equals(targetCount.getSessionId())) {
            throw AppException.badRequest("Запись подсчёта принадлежит другой сессии");
        }

        applyActualCount(targetCount, actualQuantity, notes);
    }

    private void applyActualCount(InventoryCount targetCount, BigDecimal actualQuantity, String notes) {
        targetCount.setActualQuantity(actualQuantity);
        targetCount.setDiscrepancy(actualQuantity.subtract(targetCount.getExpectedQuantity()));
        if (notes != null) {
            targetCount.setNotes(notes);
        }
        countRepository.save(targetCount);

        log.info("Actual count recorded. Discrepancy: {}", targetCount.getDiscrepancy());
    }

    @Transactional
    public Map<String, Object> completeInventory(UUID sessionId, UUID userId) {
        log.info("Completing inventory session: {}", sessionId);

        InventorySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("Сессия инвентаризации не найдена"));

        if (session.getStatus() != InventorySession.SessionStatus.IN_PROGRESS) {
            throw AppException.badRequest("Сессия уже завершена");
        }

        List<InventoryCount> counts = countRepository.findBySessionId(sessionId);

        long unfilled = counts.stream()
                .filter(c -> c.getActualQuantity() == null)
                .count();

        if (unfilled > 0) {
            log.warn("Inventory session has {} unfilled counts", unfilled);
        }

        List<InventoryCount> discrepancies = counts.stream()
                .filter(c -> c.getActualQuantity() != null)
                .filter(c -> c.getDiscrepancy().compareTo(BigDecimal.ZERO) != 0)
                .collect(Collectors.toList());

        log.info("Found {} discrepancies", discrepancies.size());

        for (InventoryCount count : discrepancies) {
            if (count.getActualQuantity() == null) continue;
            int sign = count.getDiscrepancy().compareTo(BigDecimal.ZERO);
            if (sign < 0) {
                count.setMarkedForWriteoff(true);
                countRepository.save(count);
                log.info("Marked count {} for writeoff (недостача {})",
                        count.getCountId(), count.getDiscrepancy());
            } else if (sign > 0) {
                adjustInventory(count, userId);
            }
        }

        session.setStatus(InventorySession.SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        List<Map<String, Object>> discrepancyRows = discrepancies.stream().map(d -> {
            ProductReadModel product = d.getProductId() != null
                    ? productReadModelRepository.findById(d.getProductId()).orElse(null)
                    : null;
            Map<String, Object> row = new HashMap<>();
            row.put("productId", d.getProductId() != null ? d.getProductId().toString() : null);
            row.put("productName", product != null ? product.getName() : null);
            row.put("sku", product != null ? product.getSku() : null);
            row.put("cellId", d.getCellId() != null ? d.getCellId().toString() : "N/A");
            row.put("expected", d.getExpectedQuantity() != null ? d.getExpectedQuantity().toString() : null);
            row.put("actual", d.getActualQuantity() != null ? d.getActualQuantity().toString() : null);
            row.put("discrepancy", d.getDiscrepancy() != null ? d.getDiscrepancy().toString() : null);
            return row;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId.toString());
        result.put("warehouseId", session.getWarehouseId().toString());
        result.put("totalRecords", counts.size());
        result.put("discrepanciesCount", discrepancies.size());
        result.put("discrepancies", discrepancyRows);

        if (session.getOrganizationId() != null) {
            try {
                Map<String, Object> payload = buildInventoryReportPayload(session, counts, discrepancyRows);
                var doc = documentRegistryService.register(
                        null,
                        "inventory-report",
                        payload,
                        session.getOrganizationId(),
                        userId);
                result.put("documentId", doc.getId().toString());
                result.put("documentNumber", doc.getDocumentNumber());
                log.info("Inventory report {} registered for session {}", doc.getDocumentNumber(), sessionId);
            } catch (Exception e) {
                log.error("Не удалось сгенерировать инвентаризационную опись для сессии {}: {}",
                        sessionId, e.getMessage(), e);
                result.put("documentError", "Не удалось сгенерировать опись: " + e.getMessage());
            }
        } else {
            log.warn("Сессия {} без organizationId — пропускаем генерацию описи", sessionId);
        }

        log.info("Inventory session completed: {}", sessionId);
        return result;
    }

    private Map<String, Object> buildInventoryReportPayload(
            InventorySession session,
            List<InventoryCount> counts,
            List<Map<String, Object>> discrepancyRows) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("documentDate", LocalDateTime.now().toLocalDate().toString());
        payload.put("inventoryDate", session.getStartedAt() != null
                ? session.getStartedAt().toLocalDate().toString()
                : LocalDateTime.now().toLocalDate().toString());
        payload.put("organizationId", session.getOrganizationId() != null ? session.getOrganizationId().toString() : null);
        payload.put("senderOrganizationId", session.getOrganizationId() != null ? session.getOrganizationId().toString() : null);
        payload.put("warehouseId", session.getWarehouseId().toString());
        payload.put("reason", session.getReason() != null ? session.getReason() : "Плановая инвентаризация");
        if (session.getResponsibleUserId() != null) {
            payload.put("responsiblePerson", session.getResponsibleUserId().toString());
            payload.put("responsibleUserId", session.getResponsibleUserId().toString());
        }
        if (session.getStartedBy() != null) {
            payload.put("chairmanName", session.getStartedBy().toString());
        }
        if (session.getCommissionMembers() != null && !session.getCommissionMembers().isBlank()) {
            try {
                List<UUID> members = objectMapper.readValue(
                        session.getCommissionMembers(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, UUID.class));
                if (members != null && !members.isEmpty()) {
                    payload.put("commissionMembers", members);
                }
            } catch (Exception e) {
                log.warn("Не удалось распарсить commissionMembers сессии {}: {}",
                        session.getSessionId(), e.getMessage());
            }
        }

        java.math.BigDecimal totalExpectedQty = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalActualQty = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalBookValue = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalActualValue = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalSurplus = java.math.BigDecimal.ZERO;
        java.math.BigDecimal totalShortage = java.math.BigDecimal.ZERO;

        List<Map<String, Object>> items = new ArrayList<>();
        int idx = 1;
        for (InventoryCount c : counts) {
            ProductReadModel product = c.getProductId() != null
                    ? productReadModelRepository.findById(c.getProductId()).orElse(null)
                    : null;
            ProductBatch batch = c.getBatchId() != null
                    ? productBatchRepository.findById(c.getBatchId()).orElse(null) : null;

            java.math.BigDecimal unitPrice = batch != null && batch.getPurchasePrice() != null
                    ? batch.getPurchasePrice()
                    : (product != null && product.getPrice() != null
                            ? product.getPrice() : java.math.BigDecimal.ZERO);
            java.math.BigDecimal expQty = c.getExpectedQuantity() != null
                    ? c.getExpectedQuantity() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal actQty = c.getActualQuantity() != null
                    ? c.getActualQuantity() : java.math.BigDecimal.ZERO;
            java.math.BigDecimal expValue = unitPrice.multiply(expQty);
            java.math.BigDecimal actValue = unitPrice.multiply(actQty);
            java.math.BigDecimal diff = actQty.subtract(expQty);
            java.math.BigDecimal surplus = diff.signum() > 0 ? diff : java.math.BigDecimal.ZERO;
            java.math.BigDecimal shortage = diff.signum() < 0 ? diff.abs() : java.math.BigDecimal.ZERO;

            totalExpectedQty = totalExpectedQty.add(expQty);
            totalActualQty = totalActualQty.add(actQty);
            totalBookValue = totalBookValue.add(expValue);
            totalActualValue = totalActualValue.add(actValue);
            totalSurplus = totalSurplus.add(surplus);
            totalShortage = totalShortage.add(shortage);

            Map<String, Object> item = new HashMap<>();
            item.put("rowNumber", idx);
            item.put("lineNo", idx++);
            item.put("productId", c.getProductId() != null ? c.getProductId().toString() : null);
            item.put("productName", product != null ? product.getName() : null);
            item.put("name", product != null ? product.getName() : null);
            item.put("sku", product != null ? product.getSku() : null);
            item.put("productSku", product != null ? product.getSku() : null);
            item.put("batchId", c.getBatchId() != null ? c.getBatchId().toString() : null);
            item.put("batchNumber", batch != null ? batch.getBatchNumber() : null);
            item.put("expiryDate", batch != null && batch.getExpiryDate() != null
                    ? batch.getExpiryDate().toString() : null);
            item.put("unitOfMeasure", product != null && product.getUnitOfMeasure() != null
                    ? product.getUnitOfMeasure() : "шт");
            item.put("unit", product != null && product.getUnitOfMeasure() != null
                    ? product.getUnitOfMeasure() : "шт");
            item.put("cellId", c.getCellId() != null ? c.getCellId().toString() : null);
            item.put("unitPrice", unitPrice);
            item.put("price", unitPrice);
            item.put("expectedQty", expQty.stripTrailingZeros().toPlainString());
            item.put("expectedQuantity", expQty.stripTrailingZeros().toPlainString());
            item.put("actualQty", actQty.stripTrailingZeros().toPlainString());
            item.put("actualQuantity", actQty.stripTrailingZeros().toPlainString());
            item.put("expectedValue", expValue);
            item.put("actualValue", actValue);
            item.put("surplus", surplus.signum() > 0 ? surplus.stripTrailingZeros().toPlainString() : "");
            item.put("shortage", shortage.signum() > 0 ? shortage.stripTrailingZeros().toPlainString() : "");
            item.put("discrepancy", diff.signum() == 0 ? "0" : diff.stripTrailingZeros().toPlainString());
            items.add(item);
        }
        payload.put("items", items);
        payload.put("discrepancies", discrepancyRows);
        payload.put("totalItems", counts.size());
        payload.put("totalRecords", counts.size());
        payload.put("discrepancyCount", discrepancyRows.size());
        payload.put("discrepanciesCount", discrepancyRows.size());
        payload.put("totalExpectedQty", totalExpectedQty.stripTrailingZeros().toPlainString());
        payload.put("totalActualQty", totalActualQty.stripTrailingZeros().toPlainString());
        payload.put("totalBookValue", totalBookValue);
        payload.put("totalActualValue", totalActualValue);
        payload.put("total_amount_fact", totalActualValue);
        payload.put("total_amount_accounted", totalBookValue);
        payload.put("totalAmount", totalActualValue);
        payload.put("totalSurplus", totalSurplus.signum() > 0 ? totalSurplus.stripTrailingZeros().toPlainString() : "");
        payload.put("totalShortage", totalShortage.signum() > 0 ? totalShortage.stripTrailingZeros().toPlainString() : "");
        return payload;
    }

    private void adjustInventory(InventoryCount count, UUID userId) {
        UUID warehouseId = count.getWarehouseId();
        if (warehouseId == null) {
            log.warn("InventoryCount {} has no warehouseId — skipping adjustment", count.getCountId());
            return;
        }
        Optional<Inventory> inventoryOpt = inventoryRepository
                .findExactInventoryForUpdate(
                        count.getProductId(), count.getBatchId(), warehouseId, count.getCellId());

        if (inventoryOpt.isPresent()) {
            Inventory inventory = inventoryOpt.get();
            BigDecimal oldQuantity = inventory.getQuantity();
            inventory.setQuantity(count.getActualQuantity());
            inventory.setLastUpdated(LocalDateTime.now());
            inventoryRepository.save(inventory);

            ProductOperation operation = ProductOperation.builder()
                    .operationId(UUID.randomUUID())
                    .operationType(by.bsuir.productservice.model.enums.OperationType.INVENTORY)
                    .productId(count.getProductId())
                    .organizationId(count.getOrganizationId())
                    .warehouseId(warehouseId)
                    .quantity(count.getDiscrepancy().abs())
                    .userId(userId)
                    .operationDate(LocalDateTime.now())
                    .notes(String.format("Инвентаризация. Было: %s, стало: %s",
                            oldQuantity, count.getActualQuantity()))
                    .build();
            operationRepository.save(operation);

            BigDecimal delta = count.getActualQuantity().subtract(oldQuantity);
            InventoryEventType eventType = delta.signum() >= 0
                    ? InventoryEventType.ITEM_ADDED
                    : InventoryEventType.ITEM_REMOVED;
            Map<String, Object> extra = new HashMap<>();
            extra.put("source", "INVENTORY_CHECK");
            extra.put("countId", count.getCountId());
            extra.put("sessionId", count.getSessionId());
            inventoryEventService.recordQuantityChange(inventory, eventType,
                    oldQuantity, delta, operation.getOperationId(), userId, extra);

            if (count.getCellId() != null && count.getBatchId() != null && delta.signum() != 0) {
                BigDecimal heightDelta = computeHeightDelta(count.getBatchId(), delta.abs());
                if (heightDelta.signum() > 0) {
                    BigDecimal applied = delta.signum() > 0 ? heightDelta.negate() : heightDelta;
                    warehouseClient.adjustSlotHeight(count.getCellId(), applied);
                }
            }

            log.info("Adjusted inventory for product {} from {} to {}",
                    count.getProductId(), oldQuantity, count.getActualQuantity());
        }
    }

    @Transactional
    public void cancelInventory(UUID sessionId) {
        log.info("Cancelling inventory session: {}", sessionId);

        InventorySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("Сессия не найдена"));

        if (session.getStatus() != InventorySession.SessionStatus.IN_PROGRESS) {
            throw AppException.badRequest("Можно отменить только активную сессию");
        }

        session.setStatus(InventorySession.SessionStatus.CANCELLED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        log.info("Inventory session cancelled: {}", sessionId);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> findActiveSessionForOrg(UUID organizationId) {
        if (organizationId == null) return null;
        List<InventorySession> active = sessionRepository.findByOrganizationIdAndStatus(
                organizationId, InventorySession.SessionStatus.IN_PROGRESS);
        if (active.isEmpty()) return null;
        InventorySession session = active.stream()
                .max((a, b) -> a.getStartedAt().compareTo(b.getStartedAt()))
                .orElse(active.get(0));
        return getInventorySession(session.getSessionId());
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getInventorySession(UUID sessionId) {
        InventorySession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> AppException.notFound("Сессия не найдена"));

        List<InventoryCount> counts = countRepository.findBySessionId(sessionId);


        Set<UUID> productIds = counts.stream()
                .map(InventoryCount::getProductId)
                .collect(Collectors.toSet());
        Map<UUID, ProductReadModel> productById = productReadModelRepository
                .findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(ProductReadModel::getProductId, p -> p));

        List<Map<String, Object>> records = counts.stream()
                .map(c -> {
                    ProductReadModel p = productById.get(c.getProductId());
                    Map<String, Object> rec = new HashMap<>();
                    rec.put("countId", c.getCountId().toString());
                    rec.put("productId", c.getProductId().toString());
                    rec.put("productName", p != null ? p.getName() : null);
                    rec.put("productSku", p != null ? p.getSku() : null);
                    rec.put("batchId", c.getBatchId() != null ? c.getBatchId().toString() : null);
                    rec.put("cellId", c.getCellId() != null ? c.getCellId().toString() : null);
                    rec.put("expectedQuantity", c.getExpectedQuantity());
                    rec.put("actualQuantity", c.getActualQuantity());
                    rec.put("discrepancy", c.getDiscrepancy());
                    rec.put("markedForWriteoff", c.getMarkedForWriteoff());
                    rec.put("notes", c.getNotes());
                    rec.put("isFilled", c.getActualQuantity() != null);
                    return rec;
                })
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getSessionId().toString());
        result.put("warehouseId", session.getWarehouseId().toString());
        result.put("startedAt", session.getStartedAt().toString());
        result.put("status", session.getStatus().toString());
        result.put("totalRecords", counts.size());
        result.put("filledRecords", counts.stream().filter(c -> c.getActualQuantity() != null).count());
        result.put("records", records);

        return result;
    }

    private BigDecimal computeHeightDelta(UUID batchId, BigDecimal quantityUnits) {
        if (batchId == null || quantityUnits == null || quantityUnits.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        ProductBatch batch = productBatchRepository.findById(batchId).orElse(null);
        if (batch == null || batch.getPackageHeightCm() == null) return BigDecimal.ZERO;
        int upp = (batch.getUnitsPerPackage() != null && batch.getUnitsPerPackage() > 0)
                ? batch.getUnitsPerPackage() : 1;
        BigDecimal numPackages = quantityUnits.divide(
                BigDecimal.valueOf(upp), 0, java.math.RoundingMode.CEILING);
        return batch.getPackageHeightCm().multiply(numPackages);
    }
}
