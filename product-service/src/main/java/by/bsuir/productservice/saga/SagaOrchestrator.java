package by.bsuir.productservice.saga;

import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.SagaState;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.model.enums.SagaStatus;
import by.bsuir.productservice.model.enums.SagaType;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.SagaStateRepository;
import by.bsuir.productservice.service.DocumentRegistryService;
import by.bsuir.productservice.service.InventoryEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@Getter
@Setter
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final SagaStateRepository sagaStateRepository;
    private final ObjectMapper objectMapper;
    private final ProductBatchRepository batchRepository;
    private final InventoryRepository inventoryRepository;
    private final ProductOperationRepository operationRepository;
    private final InventoryEventService inventoryEventService;

    @Autowired(required = false)
    private DocumentRegistryService documentRegistryService;

    private final Map<UUID, ReceiveSagaState> activeSagas = new ConcurrentHashMap<>();
    private final Map<UUID, ShipSagaState> activeShipSagas = new ConcurrentHashMap<>();

    @EventListener(ContextRefreshedEvent.class)
    public void restoreInProgressSagas() {
        try {
            List<SagaState> sagas = sagaStateRepository.findByStatusIn(
                    List.of(SagaStatus.PENDING, SagaStatus.COMPENSATING));
            int restored = 0;
            for (SagaState s : sagas) {
                try {
                    if (s.getSagaType() == SagaType.RECEIVE) {
                        ReceiveSagaState state = objectMapper.readValue(s.getPayload(), ReceiveSagaState.class);
                        activeSagas.put(s.getSagaId(), state);
                        restored++;
                    } else if (s.getSagaType() == SagaType.SHIP) {
                        ShipSagaState state = objectMapper.readValue(s.getPayload(), ShipSagaState.class);
                        activeShipSagas.put(s.getSagaId(), state);
                        restored++;
                    }
                } catch (Exception e) {
                    log.error("Failed to restore saga {}: {}", s.getSagaId(), e.getMessage());
                }
            }
            log.info("Restored {} in-progress sagas from DB", restored);
        } catch (Exception e) {
            log.warn("Saga restore skipped: {}", e.getMessage());
        }
    }

    @Transactional
    public UUID startReceiveSaga(ReceiveSagaState initialState) {
        UUID sagaId = UUID.randomUUID();
        initialState.setSagaId(sagaId);
        initialState.setStatus("PENDING");
        initialState.setCurrentStep("BATCH_CREATION");

        activeSagas.put(sagaId, initialState);
        persistSaga(sagaId, SagaType.RECEIVE, initialState, SagaStatus.PENDING, null);
        log.info("Started receive saga: {}", sagaId);

        return sagaId;
    }

    @Transactional
    public UUID startShipSaga(ShipSagaState initialState) {
        UUID sagaId = UUID.randomUUID();
        initialState.setSagaId(sagaId);
        initialState.setStatus("PENDING");
        initialState.setCurrentStep("STOCK_RESERVATION");

        activeShipSagas.put(sagaId, initialState);
        persistSaga(sagaId, SagaType.SHIP, initialState, SagaStatus.PENDING, null);
        log.info("Started ship saga: {}", sagaId);

        return sagaId;
    }

    @Transactional
    public void markStepCompleted(UUID sagaId, String step, Map<String, Object> data) {
        ReceiveSagaState saga = activeSagas.get(sagaId);
        if (saga == null) {
            log.warn("Saga not found: {}", sagaId);
            return;
        }

        log.info("Saga {} completed step: {}", sagaId, step);

        switch (step) {
            case "BATCH_CREATION":
                saga.setBatchId((UUID) data.get("batchId"));
                saga.setCurrentStep("INVENTORY_UPDATE");
                break;
            case "INVENTORY_UPDATE":
                saga.setInventoryId((UUID) data.get("inventoryId"));
                saga.setCurrentStep("OPERATION_RECORD");
                break;
            case "OPERATION_RECORD":
                saga.setOperationId((UUID) data.get("operationId"));
                saga.setCurrentStep("COMPLETED");
                saga.setStatus("COMPLETED");
                break;
        }

        activeSagas.put(sagaId, saga);
        persistSaga(sagaId, SagaType.RECEIVE, saga, mapStatus(saga.getStatus()), saga.getFailureReason());
    }

    @Transactional
    public void markShipStepCompleted(UUID sagaId, String step, Map<String, Object> data) {
        ShipSagaState saga = activeShipSagas.get(sagaId);
        if (saga == null) {
            log.warn("Ship saga not found: {}", sagaId);
            return;
        }

        log.info("Ship saga {} completed step: {}", sagaId, step);

        switch (step) {
            case "STOCK_RESERVATION":
                saga.setReservationId((UUID) data.get("reservationId"));
                saga.setCurrentStep("STAGING");
                break;
            case "STAGING":
                saga.setStagingOperationId((UUID) data.get("stagingOperationId"));
                saga.setCurrentStep("DOCUMENT_GENERATION");
                break;
            case "DOCUMENT_GENERATION":
                saga.setDocumentIds(extractDocumentIds(data, saga.getDocumentIds()));
                saga.setCurrentStep("INVENTORY_UPDATE");
                break;
            case "INVENTORY_UPDATE":
                saga.setInventoryId((UUID) data.get("inventoryId"));
                saga.setCurrentStep("OPERATION_RECORD");
                break;
            case "OPERATION_RECORD":
                saga.setOperationId((UUID) data.get("operationId"));
                saga.setCurrentStep("COMPLETED");
                saga.setStatus("COMPLETED");
                break;
        }

        activeShipSagas.put(sagaId, saga);
        persistSaga(sagaId, SagaType.SHIP, saga, mapStatus(saga.getStatus()), saga.getFailureReason());
    }

    @Transactional
    public void markStepFailed(UUID sagaId, String step, String reason) {
        ReceiveSagaState saga = activeSagas.get(sagaId);
        if (saga == null) {
            log.warn("Saga not found: {}", sagaId);
            return;
        }

        log.error("Saga {} failed at step: {}, reason: {}", sagaId, step, reason);
        saga.setStatus("FAILED");
        saga.setFailureReason(reason);
        saga.setCurrentStep(step);

        activeSagas.put(sagaId, saga);
        persistSaga(sagaId, SagaType.RECEIVE, saga, SagaStatus.FAILED, reason);

        compensate(sagaId);
    }

    @Transactional
    public void markShipStepFailed(UUID sagaId, String step, String reason) {
        ShipSagaState saga = activeShipSagas.get(sagaId);
        if (saga == null) {
            log.warn("Ship saga not found: {}", sagaId);
            return;
        }

        log.error("Ship saga {} failed at step: {}, reason: {}", sagaId, step, reason);
        saga.setStatus("FAILED");
        saga.setFailureReason(reason);
        saga.setCurrentStep(step);

        activeShipSagas.put(sagaId, saga);
        persistSaga(sagaId, SagaType.SHIP, saga, SagaStatus.FAILED, reason);

        compensateShipSaga(sagaId);
    }

    @Transactional
    public void compensate(UUID sagaId) {
        ReceiveSagaState saga = activeSagas.get(sagaId);
        if (saga == null) {
            log.warn("Cannot compensate, saga not found: {}", sagaId);
            return;
        }

        log.info("Starting compensation for saga: {}", sagaId);
        saga.setStatus("COMPENSATING");
        persistSaga(sagaId, SagaType.RECEIVE, saga, SagaStatus.COMPENSATING, saga.getFailureReason());

        try {
            String currentStep = saga.getCurrentStep();

            if (stepReached(currentStep, "OPERATION_RECORD") && saga.getOperationId() != null) {
                log.info("Compensating: deleting operation record {}", saga.getOperationId());
                operationRepository.findById(saga.getOperationId())
                        .ifPresent(operationRepository::delete);
            }

            if (stepReached(currentStep, "INVENTORY_UPDATE") && saga.getInventoryId() != null) {
                log.info("Compensating: reverting inventory {} (qty -{})",
                        saga.getInventoryId(), saga.getQuantity());
                inventoryRepository.findByIdForUpdate(saga.getInventoryId()).ifPresent(inv -> {
                    BigDecimal qty = saga.getQuantity() != null ? saga.getQuantity() : BigDecimal.ZERO;
                    BigDecimal qtyBefore = inv.getQuantity();
                    BigDecimal newQty = qtyBefore.subtract(qty);
                    if (newQty.compareTo(BigDecimal.ZERO) <= 0) {
                        inventoryEventService.recordQuantityChange(inv, InventoryEventType.ITEM_REMOVED,
                                qtyBefore, qty.negate(), saga.getOperationId(), null,
                                Map.of("compensation", true, "sagaId", sagaId, "deleted", true));
                        inventoryRepository.delete(inv);
                    } else {
                        inv.setQuantity(newQty);
                        inv.setLastUpdated(LocalDateTime.now());
                        inventoryRepository.save(inv);
                        inventoryEventService.recordQuantityChange(inv, InventoryEventType.ITEM_REMOVED,
                                qtyBefore, qty.negate(), saga.getOperationId(), null,
                                Map.of("compensation", true, "sagaId", sagaId));
                    }
                });
            }

            if (stepReached(currentStep, "BATCH_CREATION") && saga.getBatchId() != null) {
                log.info("Compensating: removing batch {}", saga.getBatchId());
                batchRepository.findById(saga.getBatchId())
                        .ifPresent(batchRepository::delete);
            }

            saga.setStatus("COMPENSATED");
            log.info("Saga {} successfully compensated", sagaId);

        } catch (Exception e) {
            log.error("Compensation failed for saga {}: {}", sagaId, e.getMessage(), e);
            saga.setStatus("COMPENSATION_FAILED");
        }

        activeSagas.put(sagaId, saga);
        persistSaga(sagaId, SagaType.RECEIVE, saga, mapStatus(saga.getStatus()), saga.getFailureReason());
    }

    @Transactional
    public void compensateShipSaga(UUID sagaId) {
        ShipSagaState saga = activeShipSagas.get(sagaId);
        if (saga == null) {
            log.warn("Cannot compensate, ship saga not found: {}", sagaId);
            return;
        }

        log.info("Starting compensation for ship saga: {}", sagaId);
        saga.setStatus("COMPENSATING");
        persistSaga(sagaId, SagaType.SHIP, saga, SagaStatus.COMPENSATING, saga.getFailureReason());

        try {
            String currentStep = saga.getCurrentStep();
            BigDecimal qty = saga.getQuantity() != null ? saga.getQuantity() : BigDecimal.ZERO;

            if (shipStepReached(currentStep, "OPERATION_RECORD") && saga.getOperationId() != null) {
                log.info("Compensating: deleting operation record {}", saga.getOperationId());
                operationRepository.findById(saga.getOperationId())
                        .ifPresent(operationRepository::delete);
            }

            if (shipStepReached(currentStep, "INVENTORY_UPDATE") && saga.getInventoryId() != null) {
                log.info("Compensating: restoring inventory {} (+{})", saga.getInventoryId(), qty);
                inventoryRepository.findByIdForUpdate(saga.getInventoryId()).ifPresent(inv -> {
                    BigDecimal qtyBefore = inv.getQuantity();
                    inv.setQuantity(qtyBefore.add(qty));
                    inv.setLastUpdated(LocalDateTime.now());
                    inventoryRepository.save(inv);
                    inventoryEventService.recordQuantityChange(inv, InventoryEventType.ITEM_ADDED,
                            qtyBefore, qty, saga.getOperationId(), null,
                            Map.of("compensation", true, "sagaId", sagaId));
                });
            }

            if (shipStepReached(currentStep, "DOCUMENT_GENERATION")
                    && saga.getDocumentIds() != null
                    && !saga.getDocumentIds().isEmpty()) {
                if (documentRegistryService == null) {
                    log.warn("Compensating: documentRegistryService not wired, skipping document cleanup");
                } else {
                    for (UUID docId : saga.getDocumentIds()) {
                        try {
                            documentRegistryService.deleteDocument(docId, saga.getOrganizationId());
                            log.info("Compensating: deleted generated document {}", docId);
                        } catch (Exception ex) {
                            log.warn("Compensating: failed to delete document {}: {}", docId, ex.getMessage());
                        }
                    }
                }
            }

            if (shipStepReached(currentStep, "STAGING") && saga.getStagingOperationId() != null) {
                log.info("Compensating: removing staging operation {}", saga.getStagingOperationId());
                operationRepository.findById(saga.getStagingOperationId())
                        .ifPresent(operationRepository::delete);
            }

            if (shipStepReached(currentStep, "STOCK_RESERVATION") && saga.getReservationId() != null) {
                log.info("Compensating: releasing reservation on inventory {} (-{})",
                        saga.getReservationId(), qty);
                inventoryRepository.findByIdForUpdate(saga.getReservationId()).ifPresent(inv -> {
                    BigDecimal currentReserved = inv.getReservedQuantity() != null
                            ? inv.getReservedQuantity()
                            : BigDecimal.ZERO;
                    BigDecimal newReserved = currentReserved.subtract(qty);
                    inv.setReservedQuantity(
                            newReserved.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : newReserved);
                    inv.setLastUpdated(LocalDateTime.now());
                    inventoryRepository.save(inv);
                });
            }

            saga.setStatus("COMPENSATED");
            log.info("Ship saga {} successfully compensated", sagaId);

        } catch (Exception e) {
            log.error("Compensation failed for ship saga {}: {}", sagaId, e.getMessage(), e);
            saga.setStatus("COMPENSATION_FAILED");
        }

        activeShipSagas.put(sagaId, saga);
        persistSaga(sagaId, SagaType.SHIP, saga, mapStatus(saga.getStatus()), saga.getFailureReason());
    }

    private void persistSaga(UUID sagaId, SagaType type, Object payload, SagaStatus status, String failureReason) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            String currentStep = (payload instanceof ReceiveSagaState rs) ? rs.getCurrentStep()
                    : ((ShipSagaState) payload).getCurrentStep();

            SagaState entity = sagaStateRepository.findById(sagaId).orElseGet(() -> SagaState.builder()
                    .sagaId(sagaId)
                    .sagaType(type)
                    .createdAt(LocalDateTime.now())
                    .build());
            entity.setSagaType(type);
            entity.setStatus(status);
            entity.setCurrentStep(currentStep);
            entity.setPayload(payloadJson);
            entity.setFailureReason(failureReason);
            entity.setUpdatedAt(LocalDateTime.now());
            if (entity.getCreatedAt() == null) {
                entity.setCreatedAt(LocalDateTime.now());
            }
            sagaStateRepository.save(entity);
        } catch (Exception e) {
            log.error("Failed to persist saga {}: {}", sagaId, e.getMessage(), e);
        }
    }

    private SagaStatus mapStatus(String status) {
        try {
            return SagaStatus.valueOf(status);
        } catch (Exception e) {
            return SagaStatus.PENDING;
        }
    }

    private boolean stepReached(String currentStep, String checkStep) {
        String[] stepOrder = {"BATCH_CREATION", "INVENTORY_UPDATE", "OPERATION_RECORD", "COMPLETED"};
        int currentIndex = -1, checkIndex = -1;

        for (int i = 0; i < stepOrder.length; i++) {
            if (stepOrder[i].equals(currentStep)) currentIndex = i;
            if (stepOrder[i].equals(checkStep)) checkIndex = i;
        }

        return currentIndex >= checkIndex;
    }

    @SuppressWarnings("unchecked")
    private List<UUID> extractDocumentIds(Map<String, Object> data, List<UUID> existing) {
        List<UUID> merged = existing != null ? new ArrayList<>(existing) : new ArrayList<>();
        Object docIds = data.get("documentIds");
        if (docIds instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof UUID u) {
                    merged.add(u);
                } else if (o != null) {
                    merged.add(UUID.fromString(o.toString()));
                }
            }
        }
        Object singleDocId = data.get("documentId");
        if (singleDocId instanceof UUID u) {
            merged.add(u);
        } else if (singleDocId != null) {
            merged.add(UUID.fromString(singleDocId.toString()));
        }
        return merged;
    }

    private boolean shipStepReached(String currentStep, String checkStep) {
        String[] stepOrder = {"STOCK_RESERVATION", "STAGING", "DOCUMENT_GENERATION", "INVENTORY_UPDATE", "OPERATION_RECORD", "COMPLETED"};
        int currentIndex = -1, checkIndex = -1;

        for (int i = 0; i < stepOrder.length; i++) {
            if (stepOrder[i].equals(currentStep)) currentIndex = i;
            if (stepOrder[i].equals(checkStep)) checkIndex = i;
        }

        return currentIndex >= checkIndex;
    }

    public ReceiveSagaState getSagaState(UUID sagaId) {
        return activeSagas.get(sagaId);
    }

    public ShipSagaState getShipSagaState(UUID sagaId) {
        return activeShipSagas.get(sagaId);
    }

    public void cleanupSaga(UUID sagaId) {
        activeSagas.remove(sagaId);
        log.info("Cleaned up saga: {}", sagaId);
    }

    public void cleanupShipSaga(UUID sagaId) {
        activeShipSagas.remove(sagaId);
        log.info("Cleaned up ship saga: {}", sagaId);
    }
}

