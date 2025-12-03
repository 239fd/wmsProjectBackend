package by.bsuir.productservice.saga;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class SagaOrchestrator {

    private final Map<UUID, ReceiveSagaState> activeSagas = new ConcurrentHashMap<>();

    public UUID startReceiveSaga(ReceiveSagaState initialState) {
        UUID sagaId = UUID.randomUUID();
        initialState.setSagaId(sagaId);
        initialState.setStatus("PENDING");
        initialState.setCurrentStep("BATCH_CREATION");

        activeSagas.put(sagaId, initialState);
        log.info("Started receive saga: {}", sagaId);

        return sagaId;
    }

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
    }

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

        compensate(sagaId);
    }

    public void compensate(UUID sagaId) {
        ReceiveSagaState saga = activeSagas.get(sagaId);
        if (saga == null) {
            log.warn("Cannot compensate, saga not found: {}", sagaId);
            return;
        }

        log.info("Starting compensation for saga: {}", sagaId);
        saga.setStatus("COMPENSATING");

        try {
            String currentStep = saga.getCurrentStep();

            if (stepReached(currentStep, "OPERATION_RECORD") && saga.getOperationId() != null) {
                log.info("Compensating: deleting operation record {}", saga.getOperationId());
            }

            if (stepReached(currentStep, "INVENTORY_UPDATE") && saga.getInventoryId() != null) {
                log.info("Compensating: reverting inventory {}", saga.getInventoryId());
            }

            if (stepReached(currentStep, "BATCH_CREATION") && saga.getBatchId() != null) {
                log.info("Compensating: removing batch {}", saga.getBatchId());
            }

            saga.setStatus("COMPENSATED");
            log.info("Saga {} successfully compensated", sagaId);

        } catch (Exception e) {
            log.error("Compensation failed for saga {}: {}", sagaId, e.getMessage(), e);
            saga.setStatus("COMPENSATION_FAILED");
        }

        activeSagas.put(sagaId, saga);
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

    public ReceiveSagaState getSagaState(UUID sagaId) {
        return activeSagas.get(sagaId);
    }

    public void cleanupSaga(UUID sagaId) {
        activeSagas.remove(sagaId);
        log.info("Cleaned up saga: {}", sagaId);
    }
}

