package by.bsuir.productservice.service;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.*;
import by.bsuir.productservice.repository.*;
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




    @Transactional
    public UUID startInventory(UUID warehouseId, UUID userId, String notes) {
        log.info("Starting inventory check for warehouse: {}", warehouseId);


        List<InventorySession> activeSessions = sessionRepository.findByStatus(
                InventorySession.SessionStatus.IN_PROGRESS);

        for (InventorySession session : activeSessions) {
            if (session.getWarehouseId().equals(warehouseId)) {
                throw AppException.conflict("На складе уже идёт инвентаризация. Session ID: "
                        + session.getSessionId());
            }
        }


        UUID sessionId = UUID.randomUUID();
        InventorySession session = InventorySession.builder()
                .sessionId(sessionId)
                .warehouseId(warehouseId)
                .startedBy(userId)
                .startedAt(LocalDateTime.now())
                .status(InventorySession.SessionStatus.IN_PROGRESS)
                .notes(notes)
                .build();
        sessionRepository.save(session);


        List<Inventory> currentInventory = inventoryRepository.findByWarehouseId(warehouseId);

        log.info("Creating snapshot of {} inventory records", currentInventory.size());

        for (Inventory inv : currentInventory) {
            InventoryCount count = InventoryCount.builder()
                    .countId(UUID.randomUUID())
                    .sessionId(sessionId)
                    .productId(inv.getProductId())
                    .batchId(inv.getBatchId())
                    .cellId(inv.getCellId())
                    .expectedQuantity(inv.getQuantity())
                    .actualQuantity(null)
                    .discrepancy(BigDecimal.ZERO)
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
            if (count.getActualQuantity() != null) {
                adjustInventory(count, userId);
            }
        }


        session.setStatus(InventorySession.SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        sessionRepository.save(session);


        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", sessionId.toString());
        result.put("warehouseId", session.getWarehouseId().toString());
        result.put("totalRecords", counts.size());
        result.put("discrepanciesCount", discrepancies.size());
        result.put("discrepancies", discrepancies.stream().map(d -> Map.of(
                "productId", d.getProductId().toString(),
                "cellId", d.getCellId() != null ? d.getCellId().toString() : "N/A",
                "expected", d.getExpectedQuantity().toString(),
                "actual", d.getActualQuantity().toString(),
                "discrepancy", d.getDiscrepancy().toString()
        )).collect(Collectors.toList()));

        log.info("Inventory session completed: {}", sessionId);
        return result;
    }




    private void adjustInventory(InventoryCount count, UUID userId) {
        Optional<Inventory> inventoryOpt = inventoryRepository
                .findByProductIdAndWarehouseId(count.getProductId(), count.getSessionId());

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
                    .warehouseId(count.getSessionId())
                    .quantity(count.getDiscrepancy().abs())
                    .userId(userId)
                    .operationDate(LocalDateTime.now())
                    .notes(String.format("Инвентаризация. Было: %s, стало: %s",
                            oldQuantity, count.getActualQuantity()))
                    .build();
            operationRepository.save(operation);

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

        Map<String, Object> result = new HashMap<>();
        result.put("sessionId", session.getSessionId().toString());
        result.put("warehouseId", session.getWarehouseId().toString());
        result.put("startedAt", session.getStartedAt().toString());
        result.put("status", session.getStatus().toString());
        result.put("totalRecords", counts.size());
        result.put("filledRecords", counts.stream().filter(c -> c.getActualQuantity() != null).count());

        return result;
    }
}
