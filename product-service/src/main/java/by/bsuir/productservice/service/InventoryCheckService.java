package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.StartInventoryRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryCount;
import by.bsuir.productservice.model.entity.InventorySession;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.repository.InventoryCountRepository;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.InventorySessionRepository;
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
    private final ObjectMapper objectMapper;
    private final InventoryEventService inventoryEventService;
    private final DocumentRegistryService documentRegistryService;

    @Transactional
    public UUID startInventory(UUID warehouseId, UUID userId, String notes) {
        return startInventoryInternal(warehouseId, userId, null, null, null, null, notes);
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
                throw AppException.conflict("На складе уже идёт инвентаризация. Session ID: "
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

        List<InventoryCount> counts = countRepository.findBySessionId(sessionId);
        InventoryCount targetCount = counts.stream()
                .filter(c -> c.getProductId().equals(productId) &&
                            (cellId == null || cellId.equals(c.getCellId())))
                .findFirst()
                .orElseThrow(() -> AppException.notFound("Запись подсчёта не найдена"));

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
                // Недостача — НЕ трогаем учётное количество (quantity).
                // Помечаем для списания: бухгалтер списывает через WriteoffPage,
                // тогда и quantity уменьшится на величину недостачи.
                count.setMarkedForWriteoff(true);
                countRepository.save(count);
                log.info("Marked count {} for writeoff (недостача {}), inventory.quantity не меняем",
                        count.getCountId(), count.getDiscrepancy());
            } else if (sign > 0) {
                // Излишек — обновляем учётное количество вверх (приходуем).
                adjustInventory(count, userId);
            }
        }

        session.setStatus(InventorySession.SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);

        List<Map<String, Object>> discrepancyRows = discrepancies.stream().map(d -> {
            Map<String, Object> row = new HashMap<>();
            row.put("productId", d.getProductId() != null ? d.getProductId().toString() : null);
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

        // Генерируем инвентаризационную опись (документ типа inventory-report).
        // Любая ошибка логируется и НЕ ломает завершение инвентаризации.
        if (session.getOrganizationId() != null) {
            try {
                Map<String, Object> payload = buildInventoryReportPayload(session, counts, discrepancyRows);
                var doc = documentRegistryService.register(
                        null, // inventory-check не привязан к ProductOperation напрямую
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
        payload.put("warehouseId", session.getWarehouseId().toString());
        payload.put("reason", session.getReason() != null ? session.getReason() : "Плановая инвентаризация");
        payload.put("totalRecords", counts.size());
        payload.put("discrepanciesCount", discrepancyRows.size());
        // items для шаблона — нумерация позиций для опции, потом enrichmentService догрузит названия
        List<Map<String, Object>> items = new ArrayList<>();
        int idx = 1;
        for (InventoryCount c : counts) {
            Map<String, Object> item = new HashMap<>();
            item.put("rowNumber", idx++);
            item.put("productId", c.getProductId() != null ? c.getProductId().toString() : null);
            item.put("cellId", c.getCellId() != null ? c.getCellId().toString() : null);
            item.put("expectedQuantity", c.getExpectedQuantity() != null ? c.getExpectedQuantity().toString() : "0");
            item.put("actualQuantity", c.getActualQuantity() != null ? c.getActualQuantity().toString() : "0");
            item.put("discrepancy", c.getDiscrepancy() != null ? c.getDiscrepancy().toString() : "0");
            items.add(item);
        }
        payload.put("items", items);
        payload.put("discrepancies", discrepancyRows);
        return payload;
    }

    private void adjustInventory(InventoryCount count, UUID userId) {
        UUID warehouseId = count.getWarehouseId();
        if (warehouseId == null) {
            log.warn("InventoryCount {} has no warehouseId — skipping adjustment", count.getCountId());
            return;
        }
        Optional<Inventory> inventoryOpt = inventoryRepository
                .findByProductIdAndWarehouseIdForUpdate(count.getProductId(), warehouseId);

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
}
