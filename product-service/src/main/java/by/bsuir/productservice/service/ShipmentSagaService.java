package by.bsuir.productservice.service;

import by.bsuir.productservice.dto.request.ShipProductRequest;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryEvent;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductOperationEvent;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.InventoryEventRepository;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationEventRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.saga.SagaOrchestrator;
import by.bsuir.productservice.saga.ShipSagaState;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShipmentSagaService {

    private final SagaOrchestrator sagaOrchestrator;
    private final InventoryRepository inventoryRepository;
    private final ProductOperationRepository operationRepository;
    private final FEFOService fefoService;
    private final InventoryEventRepository inventoryEventRepository;
    private final ProductOperationEventRepository productOperationEventRepository;
    private final ObjectMapper objectMapper;
    private final InventoryEventService inventoryEventService;

    @Transactional
    public UUID startShipmentSaga(ShipProductRequest request) {
        log.info("Starting shipment saga for product {} from warehouse {}, quantity: {}",
                request.productId(), request.warehouseId(), request.quantity());

        ShipSagaState initialState = ShipSagaState.builder()
                .productId(request.productId())
                .warehouseId(request.warehouseId())
                .organizationId(null)
                .userId(request.userId())
                .quantity(request.quantity())
                .build();

        UUID sagaId = sagaOrchestrator.startShipSaga(initialState);

        try {
            executeStockReservation(sagaId, request);
            executeStagingRecord(sagaId, request);

            log.info("Shipment saga {} moved to STAGING, awaiting completion", sagaId);
            return sagaId;

        } catch (Exception e) {
            log.error("Shipment saga {} failed at staging: {}", sagaId, e.getMessage(), e);
            throw e;
        }
    }

    @Transactional
    public void completeShipmentSaga(UUID sagaId) {
        ShipSagaState state = sagaOrchestrator.getShipSagaState(sagaId);
        if (state == null) {
            throw AppException.notFound("Сага отгрузки не найдена: " + sagaId);
        }
        if (!"STAGING".equals(state.getCurrentStep())) {
            throw AppException.badRequest("Сага отгрузки не находится в статусе STAGING");
        }

        ShipProductRequest request = new ShipProductRequest(
                state.getProductId(),
                null,
                state.getWarehouseId(),
                null,
                state.getQuantity(),
                state.getUserId(),
                null,
                null
        );

        try {
            executeDocumentGeneration(sagaId, request);
            executeInventoryUpdate(sagaId, request);
            executeOperationRecord(sagaId, request);

            log.info("Shipment saga {} completed successfully", sagaId);
        } catch (Exception e) {
            log.error("Shipment saga {} failed at completion: {}", sagaId, e.getMessage(), e);
            throw e;
        }
    }

    private void executeStockReservation(UUID sagaId, ShipProductRequest request) {
        try {
            log.info("Saga {}: executing STOCK_RESERVATION", sagaId);

            Inventory inventory = findInventoryForShipment(
                    request.productId(),
                    request.warehouseId(),
                    request.quantity()
            );

            if (inventory == null) {
                throw AppException.badRequest("Недостаточно товара на складе");
            }

            Map<String, Object> data = new HashMap<>();
            data.put("reservationId", inventory.getInventoryId());
            sagaOrchestrator.markShipStepCompleted(sagaId, "STOCK_RESERVATION", data);

        } catch (Exception e) {
            log.error("Saga {}: STOCK_RESERVATION failed", sagaId, e);
            sagaOrchestrator.markShipStepFailed(sagaId, "STOCK_RESERVATION", e.getMessage());
            throw AppException.internalError("Не удалось зарезервировать товар: " + e.getMessage());
        }
    }

    private void executeStagingRecord(UUID sagaId, ShipProductRequest request) {
        try {
            log.info("Saga {}: executing STAGING", sagaId);

            ProductOperation stagingOp = ProductOperation.builder()
                    .operationId(UUID.randomUUID())
                    .operationType(OperationType.STAGING)
                    .productId(request.productId())
                    .warehouseId(request.warehouseId())
                    .quantity(request.quantity())
                    .userId(request.userId())
                    .operationDate(LocalDateTime.now())
                    .notes("Товар перемещён в зону отгрузки")
                    .build();

            operationRepository.save(stagingOp);
            saveOperationEvent(stagingOp, "OPERATION_RECORDED", buildOperationEventPayload(stagingOp));

            Map<String, Object> data = new HashMap<>();
            data.put("stagingOperationId", stagingOp.getOperationId());
            sagaOrchestrator.markShipStepCompleted(sagaId, "STAGING", data);

        } catch (Exception e) {
            log.error("Saga {}: STAGING failed", sagaId, e);
            sagaOrchestrator.markShipStepFailed(sagaId, "STAGING", e.getMessage());
            throw AppException.internalError("Не удалось зафиксировать перемещение в зону отгрузки: " + e.getMessage());
        }
    }

    private void executeDocumentGeneration(UUID sagaId, ShipProductRequest request) {
        try {
            log.info("Saga {}: executing DOCUMENT_GENERATION", sagaId);

            UUID documentId = UUID.randomUUID();

            Map<String, Object> data = new HashMap<>();
            data.put("documentId", documentId);
            sagaOrchestrator.markShipStepCompleted(sagaId, "DOCUMENT_GENERATION", data);

        } catch (Exception e) {
            log.error("Saga {}: DOCUMENT_GENERATION failed", sagaId, e);
            sagaOrchestrator.markShipStepFailed(sagaId, "DOCUMENT_GENERATION", e.getMessage());
            throw AppException.internalError("Не удалось сгенерировать документ: " + e.getMessage());
        }
    }

    private void executeInventoryUpdate(UUID sagaId, ShipProductRequest request) {
        try {
            log.info("Saga {}: executing INVENTORY_UPDATE", sagaId);

            ShipSagaState state = sagaOrchestrator.getShipSagaState(sagaId);
            UUID reservationInventoryId = state != null ? state.getReservationId() : null;

            Inventory inventory;
            if (reservationInventoryId != null) {
                inventory = inventoryRepository.findByIdForUpdate(reservationInventoryId).orElse(null);
            } else {
                inventory = findInventoryForShipment(
                        request.productId(),
                        request.warehouseId(),
                        request.quantity()
                );
            }

            if (inventory == null) {
                throw AppException.badRequest("Недостаточно товара на складе");
            }

            BigDecimal qtyBefore = inventory.getQuantity();
            inventory.setQuantity(qtyBefore.subtract(request.quantity()));

            if (inventory.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
                inventoryEventService.recordQuantityChange(inventory, InventoryEventType.ITEM_REMOVED,
                        qtyBefore, request.quantity().negate(), null, request.userId(),
                        Map.of("sagaId", sagaId, "depleted", true));
                inventoryRepository.delete(inventory);
                log.info("Inventory depleted and removed: {}", inventory.getInventoryId());
            } else {
                inventoryRepository.save(inventory);
                inventoryEventService.recordQuantityChange(inventory, InventoryEventType.ITEM_REMOVED,
                        qtyBefore, request.quantity().negate(), null, request.userId(),
                        Map.of("sagaId", sagaId));
            }

            Map<String, Object> data = new HashMap<>();
            data.put("inventoryId", inventory.getInventoryId());
            sagaOrchestrator.markShipStepCompleted(sagaId, "INVENTORY_UPDATE", data);

        } catch (Exception e) {
            log.error("Saga {}: INVENTORY_UPDATE failed", sagaId, e);
            sagaOrchestrator.markShipStepFailed(sagaId, "INVENTORY_UPDATE", e.getMessage());
            throw AppException.internalError("Не удалось обновить остатки: " + e.getMessage());
        }
    }

    private void executeOperationRecord(UUID sagaId, ShipProductRequest request) {
        try {
            log.info("Saga {}: executing OPERATION_RECORD", sagaId);

            ProductOperation operation = ProductOperation.builder()
                    .operationId(UUID.randomUUID())
                    .operationType(OperationType.SHIPMENT)
                    .productId(request.productId())
                    .warehouseId(request.warehouseId())
                    .quantity(request.quantity())
                    .userId(request.userId())
                    .operationDate(LocalDateTime.now())
                    .notes(request.notes())
                    .build();

            operationRepository.save(operation);
            saveOperationEvent(operation, "OPERATION_RECORDED", buildOperationEventPayload(operation));

            Map<String, Object> data = new HashMap<>();
            data.put("operationId", operation.getOperationId());
            sagaOrchestrator.markShipStepCompleted(sagaId, "OPERATION_RECORD", data);

        } catch (Exception e) {
            log.error("Saga {}: OPERATION_RECORD failed", sagaId, e);
            sagaOrchestrator.markShipStepFailed(sagaId, "OPERATION_RECORD", e.getMessage());
            throw AppException.internalError("Не удалось записать операцию: " + e.getMessage());
        }
    }

    private Inventory findInventoryForShipment(UUID productId, UUID warehouseId, BigDecimal quantity) {
        List<FEFOService.InventoryAllocation> allocations = fefoService.selectInventoryByFEFO(
                productId,
                warehouseId,
                quantity
        );

        if (allocations.isEmpty()) {
            return null;
        }

        UUID inventoryId = allocations.get(0).getInventoryId();
        return inventoryRepository.findById(inventoryId).orElse(null);
    }

    private Map<String, Object> buildOperationEventPayload(ProductOperation operation) {
        Map<String, Object> payload = new HashMap<>();
        if (operation.getOperationType() != null) {
            payload.put("operationType", operation.getOperationType().name());
        }
        if (operation.getProductId() != null) {
            payload.put("productId", operation.getProductId().toString());
        }
        if (operation.getWarehouseId() != null) {
            payload.put("warehouseId", operation.getWarehouseId().toString());
        }
        if (operation.getQuantity() != null) {
            payload.put("quantity", operation.getQuantity().toString());
        }
        return payload;
    }

    private void saveOperationEvent(ProductOperation operation, String eventType, Map<String, Object> payload) {
        Map<String, Object> eventData = new LinkedHashMap<>(payload);
        eventData.put("batchId", operation.getBatchId() != null ? operation.getBatchId().toString() : null);
        eventData.put("fromCellId", operation.getFromCellId() != null ? operation.getFromCellId().toString() : null);
        eventData.put("toCellId", operation.getToCellId() != null ? operation.getToCellId().toString() : null);
        eventData.put("eventAt", LocalDateTime.now().toString());

        ProductOperationEvent event = ProductOperationEvent.builder()
                .operationId(operation.getOperationId())
                .eventType(eventType)
                .eventData(objectMapper.valueToTree(eventData))
                .eventVersion(productOperationEventRepository.findMaxEventVersionByOperationId(operation.getOperationId()) + 1)
                .createdAt(LocalDateTime.now())
                .build();
        productOperationEventRepository.save(event);
    }
}