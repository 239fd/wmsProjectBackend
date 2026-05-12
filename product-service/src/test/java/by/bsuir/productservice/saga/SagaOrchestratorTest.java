package by.bsuir.productservice.saga;

import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.SagaState;
import by.bsuir.productservice.model.enums.SagaStatus;
import by.bsuir.productservice.model.enums.SagaType;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.SagaStateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SagaOrchestrator — модульные тесты")
class SagaOrchestratorTest {

    @Mock private SagaStateRepository sagaStateRepository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();
    @Mock private ProductBatchRepository batchRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private ProductOperationRepository operationRepository;
    @Mock private by.bsuir.productservice.service.InventoryEventService inventoryEventService;

    @InjectMocks private SagaOrchestrator orchestrator;

    @Test
    @DisplayName("startReceiveSaga: создаёт sagaId, ставит PENDING + BATCH_CREATION, persist'ит state")
    void startReceiveSaga_ShouldCreateAndPersist() {
        ReceiveSagaState state = ReceiveSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.TEN).build();

        UUID sagaId = orchestrator.startReceiveSaga(state);

        assertThat(sagaId).isNotNull();
        assertThat(state.getSagaId()).isEqualTo(sagaId);
        assertThat(state.getStatus()).isEqualTo("PENDING");
        assertThat(state.getCurrentStep()).isEqualTo("BATCH_CREATION");
        assertThat(orchestrator.getSagaState(sagaId)).isSameAs(state);

        ArgumentCaptor<SagaState> captor = ArgumentCaptor.forClass(SagaState.class);
        verify(sagaStateRepository).save(captor.capture());
        assertThat(captor.getValue().getSagaType()).isEqualTo(SagaType.RECEIVE);
        assertThat(captor.getValue().getStatus()).isEqualTo(SagaStatus.PENDING);
        assertThat(captor.getValue().getCurrentStep()).isEqualTo("BATCH_CREATION");
        assertThat(captor.getValue().getPayload()).isNotBlank();
    }

    @Test
    @DisplayName("markStepCompleted: BATCH_CREATION → INVENTORY_UPDATE → OPERATION_RECORD → COMPLETED")
    void markStepCompleted_ShouldAdvanceThroughSteps() {
        UUID sagaId = orchestrator.startReceiveSaga(ReceiveSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.TEN).build());

        UUID batchId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        orchestrator.markStepCompleted(sagaId, "BATCH_CREATION", Map.of("batchId", batchId));
        assertThat(orchestrator.getSagaState(sagaId).getCurrentStep()).isEqualTo("INVENTORY_UPDATE");
        assertThat(orchestrator.getSagaState(sagaId).getBatchId()).isEqualTo(batchId);

        orchestrator.markStepCompleted(sagaId, "INVENTORY_UPDATE", Map.of("inventoryId", invId));
        assertThat(orchestrator.getSagaState(sagaId).getCurrentStep()).isEqualTo("OPERATION_RECORD");
        assertThat(orchestrator.getSagaState(sagaId).getInventoryId()).isEqualTo(invId);

        orchestrator.markStepCompleted(sagaId, "OPERATION_RECORD", Map.of("operationId", opId));
        ReceiveSagaState finalState = orchestrator.getSagaState(sagaId);
        assertThat(finalState.getCurrentStep()).isEqualTo("COMPLETED");
        assertThat(finalState.getStatus()).isEqualTo("COMPLETED");
        assertThat(finalState.getOperationId()).isEqualTo(opId);
    }

    @Test
    @DisplayName("markStepCompleted: неизвестный sagaId → no-op без падений")
    void markStepCompleted_GivenUnknownSagaId_ShouldNoOp() {
        orchestrator.markStepCompleted(UUID.randomUUID(), "BATCH_CREATION", Map.of());
        verify(sagaStateRepository, never()).save(any());
    }

    @Test
    @DisplayName("markStepFailed: receive после OPERATION_RECORD → удаляет operation/inventory/batch, статус COMPENSATED")
    void markStepFailed_AfterFullProgress_ShouldFullyCompensate() {
        UUID productId = UUID.randomUUID();
        UUID sagaId = orchestrator.startReceiveSaga(ReceiveSagaState.builder()
                .productId(productId).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.valueOf(5)).build());
        UUID batchId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();
        orchestrator.markStepCompleted(sagaId, "BATCH_CREATION", Map.of("batchId", batchId));
        orchestrator.markStepCompleted(sagaId, "INVENTORY_UPDATE", Map.of("inventoryId", invId));
        orchestrator.markStepCompleted(sagaId, "OPERATION_RECORD", Map.of("operationId", opId));

        ProductOperation op = ProductOperation.builder().operationId(opId).build();
        Inventory inv = Inventory.builder().inventoryId(invId).quantity(BigDecimal.valueOf(15))
                .reservedQuantity(BigDecimal.ZERO).build();
        ProductBatch batch = ProductBatch.builder().batchId(batchId).build();
        when(operationRepository.findById(opId)).thenReturn(Optional.of(op));
        when(inventoryRepository.findById(invId)).thenReturn(Optional.of(inv));
        when(batchRepository.findById(batchId)).thenReturn(Optional.of(batch));

        orchestrator.markStepFailed(sagaId, "OPERATION_RECORD", "оператор отменил");

        verify(operationRepository).delete(op);
        verify(inventoryRepository).save(inv);
        assertThat(inv.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(10));
        verify(batchRepository).delete(batch);

        ReceiveSagaState state = orchestrator.getSagaState(sagaId);
        assertThat(state.getStatus()).isEqualTo("COMPENSATED");
        assertThat(state.getFailureReason()).isEqualTo("оператор отменил");
    }

    @Test
    @DisplayName("compensate: после INVENTORY_UPDATE с qty>=current → удаляет inventory полностью")
    void compensate_WhenQtyConsumesAllInventory_ShouldDeleteInventory() {
        UUID sagaId = orchestrator.startReceiveSaga(ReceiveSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.valueOf(10)).build());
        UUID invId = UUID.randomUUID();
        orchestrator.markStepCompleted(sagaId, "BATCH_CREATION", Map.of("batchId", UUID.randomUUID()));
        orchestrator.markStepCompleted(sagaId, "INVENTORY_UPDATE", Map.of("inventoryId", invId));

        Inventory inv = Inventory.builder().inventoryId(invId).quantity(BigDecimal.valueOf(10)).build();
        when(inventoryRepository.findById(invId)).thenReturn(Optional.of(inv));

        orchestrator.markStepFailed(sagaId, "INVENTORY_UPDATE", "fail");

        verify(inventoryRepository).delete(inv);
        verify(inventoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("compensate: упало в BATCH_CREATION → ничего к компенсации, просто COMPENSATED")
    void compensate_WhenFailedAtBatchCreation_ShouldNotTouchInventoryOrOperation() {
        UUID sagaId = orchestrator.startReceiveSaga(ReceiveSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.TEN).build());

        orchestrator.markStepFailed(sagaId, "BATCH_CREATION", "no batch");

        verify(operationRepository, never()).delete(any(ProductOperation.class));
        verify(inventoryRepository, never()).delete(any(Inventory.class));
        verify(batchRepository, never()).delete(any(ProductBatch.class));
        assertThat(orchestrator.getSagaState(sagaId).getStatus()).isEqualTo("COMPENSATED");
    }

    @Test
    @DisplayName("compensate: исключение в delete → статус COMPENSATION_FAILED")
    void compensate_WhenDeleteThrows_ShouldMarkCompensationFailed() {
        UUID sagaId = orchestrator.startReceiveSaga(ReceiveSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.ONE).build());
        UUID opId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        UUID batchId = UUID.randomUUID();
        orchestrator.markStepCompleted(sagaId, "BATCH_CREATION", Map.of("batchId", batchId));
        orchestrator.markStepCompleted(sagaId, "INVENTORY_UPDATE", Map.of("inventoryId", invId));
        orchestrator.markStepCompleted(sagaId, "OPERATION_RECORD", Map.of("operationId", opId));

        when(operationRepository.findById(opId))
                .thenThrow(new RuntimeException("DB connection lost"));

        orchestrator.markStepFailed(sagaId, "OPERATION_RECORD", "fail");

        assertThat(orchestrator.getSagaState(sagaId).getStatus()).isEqualTo("COMPENSATION_FAILED");
    }

    @Test
    @DisplayName("startShipSaga: ставит PENDING + STOCK_RESERVATION, persist'ит SHIP-state")
    void startShipSaga_ShouldCreateAndPersist() {
        ShipSagaState state = ShipSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.valueOf(5)).build();

        UUID sagaId = orchestrator.startShipSaga(state);

        assertThat(state.getSagaId()).isEqualTo(sagaId);
        assertThat(state.getStatus()).isEqualTo("PENDING");
        assertThat(state.getCurrentStep()).isEqualTo("STOCK_RESERVATION");
        verify(sagaStateRepository).save(any(SagaState.class));
    }

    @Test
    @DisplayName("markShipStepCompleted: проходит весь happy path до COMPLETED")
    void markShipStepCompleted_ShouldAdvanceToCompleted() {
        UUID sagaId = orchestrator.startShipSaga(ShipSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.TEN).build());

        orchestrator.markShipStepCompleted(sagaId, "STOCK_RESERVATION", Map.of("reservationId", UUID.randomUUID()));
        assertThat(orchestrator.getShipSagaState(sagaId).getCurrentStep()).isEqualTo("STAGING");

        orchestrator.markShipStepCompleted(sagaId, "STAGING", Map.of("stagingOperationId", UUID.randomUUID()));
        assertThat(orchestrator.getShipSagaState(sagaId).getCurrentStep()).isEqualTo("DOCUMENT_GENERATION");

        orchestrator.markShipStepCompleted(sagaId, "DOCUMENT_GENERATION", Map.of("documentId", UUID.randomUUID()));
        assertThat(orchestrator.getShipSagaState(sagaId).getCurrentStep()).isEqualTo("INVENTORY_UPDATE");

        orchestrator.markShipStepCompleted(sagaId, "INVENTORY_UPDATE", Map.of("inventoryId", UUID.randomUUID()));
        assertThat(orchestrator.getShipSagaState(sagaId).getCurrentStep()).isEqualTo("OPERATION_RECORD");

        orchestrator.markShipStepCompleted(sagaId, "OPERATION_RECORD", Map.of("operationId", UUID.randomUUID()));
        assertThat(orchestrator.getShipSagaState(sagaId).getCurrentStep()).isEqualTo("COMPLETED");
        assertThat(orchestrator.getShipSagaState(sagaId).getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("compensateShipSaga: после OPERATION_RECORD → восстанавливает inventory, освобождает резерв, удаляет staging+operation")
    void compensateShipSaga_AfterFullProgress_ShouldRollbackAll() {
        UUID sagaId = orchestrator.startShipSaga(ShipSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.valueOf(5)).build());
        UUID resId = UUID.randomUUID();
        UUID stagingId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID invId = UUID.randomUUID();
        UUID opId = UUID.randomUUID();

        orchestrator.markShipStepCompleted(sagaId, "STOCK_RESERVATION", Map.of("reservationId", resId));
        orchestrator.markShipStepCompleted(sagaId, "STAGING", Map.of("stagingOperationId", stagingId));
        orchestrator.markShipStepCompleted(sagaId, "DOCUMENT_GENERATION", Map.of("documentId", docId));
        orchestrator.markShipStepCompleted(sagaId, "INVENTORY_UPDATE", Map.of("inventoryId", invId));
        orchestrator.markShipStepCompleted(sagaId, "OPERATION_RECORD", Map.of("operationId", opId));

        ProductOperation op = ProductOperation.builder().operationId(opId).build();
        ProductOperation staging = ProductOperation.builder().operationId(stagingId).build();
        Inventory inv = Inventory.builder().inventoryId(invId)
                .quantity(BigDecimal.valueOf(20)).reservedQuantity(BigDecimal.valueOf(5)).build();
        Inventory reserved = Inventory.builder().inventoryId(resId)
                .quantity(BigDecimal.valueOf(20)).reservedQuantity(BigDecimal.valueOf(5)).build();
        when(operationRepository.findById(opId)).thenReturn(Optional.of(op));
        when(operationRepository.findById(stagingId)).thenReturn(Optional.of(staging));
        when(inventoryRepository.findById(invId)).thenReturn(Optional.of(inv));
        when(inventoryRepository.findById(resId)).thenReturn(Optional.of(reserved));

        orchestrator.markShipStepFailed(sagaId, "OPERATION_RECORD", "fail");

        verify(operationRepository).delete(op);
        verify(operationRepository).delete(staging);

        assertThat(inv.getQuantity()).isEqualByComparingTo(BigDecimal.valueOf(25));

        assertThat(reserved.getReservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);

        ShipSagaState state = orchestrator.getShipSagaState(sagaId);
        assertThat(state.getStatus()).isEqualTo("COMPENSATED");
    }

    @Test
    @DisplayName("compensateShipSaga: reservedQuantity < qty → не уходит в минус, обнуляется")
    void compensateShipSaga_ReservedClampedAtZero() {
        UUID sagaId = orchestrator.startShipSaga(ShipSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.TEN).build());
        UUID resId = UUID.randomUUID();
        orchestrator.markShipStepCompleted(sagaId, "STOCK_RESERVATION", Map.of("reservationId", resId));

        Inventory inv = Inventory.builder().inventoryId(resId)
                .quantity(BigDecimal.TEN).reservedQuantity(BigDecimal.valueOf(3)).build();
        when(inventoryRepository.findById(resId)).thenReturn(Optional.of(inv));

        orchestrator.markShipStepFailed(sagaId, "STOCK_RESERVATION", "race");

        assertThat(inv.getReservedQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("restoreInProgressSagas: загружает PENDING/COMPENSATING из БД в in-memory map")
    void restoreInProgressSagas_ShouldRestoreFromDb() throws Exception {
        ReceiveSagaState saga1 = ReceiveSagaState.builder()
                .sagaId(UUID.randomUUID()).productId(UUID.randomUUID())
                .warehouseId(UUID.randomUUID()).quantity(BigDecimal.ONE)
                .currentStep("INVENTORY_UPDATE").status("PENDING").build();
        ShipSagaState saga2 = ShipSagaState.builder()
                .sagaId(UUID.randomUUID()).productId(UUID.randomUUID())
                .warehouseId(UUID.randomUUID()).quantity(BigDecimal.TEN)
                .currentStep("STAGING").status("PENDING").build();

        SagaState s1 = SagaState.builder().sagaId(saga1.getSagaId()).sagaType(SagaType.RECEIVE)
                .status(SagaStatus.PENDING).currentStep("INVENTORY_UPDATE")
                .payload(objectMapper.writeValueAsString(saga1)).build();
        SagaState s2 = SagaState.builder().sagaId(saga2.getSagaId()).sagaType(SagaType.SHIP)
                .status(SagaStatus.COMPENSATING).currentStep("STAGING")
                .payload(objectMapper.writeValueAsString(saga2)).build();

        when(sagaStateRepository.findByStatusIn(eq(List.of(SagaStatus.PENDING, SagaStatus.COMPENSATING))))
                .thenReturn(List.of(s1, s2));

        orchestrator.restoreInProgressSagas();

        assertThat(orchestrator.getSagaState(saga1.getSagaId())).isNotNull();
        assertThat(orchestrator.getShipSagaState(saga2.getSagaId())).isNotNull();
    }

    @Test
    @DisplayName("cleanupSaga / cleanupShipSaga: убирают state из памяти")
    void cleanupSaga_ShouldRemoveFromMemory() {
        UUID sagaId = orchestrator.startReceiveSaga(ReceiveSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.ONE).build());
        UUID shipId = orchestrator.startShipSaga(ShipSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID())
                .quantity(BigDecimal.ONE).build());

        orchestrator.cleanupSaga(sagaId);
        orchestrator.cleanupShipSaga(shipId);

        assertThat(orchestrator.getSagaState(sagaId)).isNull();
        assertThat(orchestrator.getShipSagaState(shipId)).isNull();
    }

    @Test
    @DisplayName("startReceiveSaga: 3 параллельные саги — у каждой свой sagaId, persist 3 раза")
    void startReceiveSaga_MultipleSagas_ShouldBeIsolated() {
        UUID s1 = orchestrator.startReceiveSaga(ReceiveSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID()).quantity(BigDecimal.ONE).build());
        UUID s2 = orchestrator.startReceiveSaga(ReceiveSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID()).quantity(BigDecimal.ONE).build());
        UUID s3 = orchestrator.startReceiveSaga(ReceiveSagaState.builder()
                .productId(UUID.randomUUID()).warehouseId(UUID.randomUUID()).quantity(BigDecimal.ONE).build());

        assertThat(s1).isNotEqualTo(s2).isNotEqualTo(s3);
        assertThat(s2).isNotEqualTo(s3);
        verify(sagaStateRepository, times(3)).save(any(SagaState.class));
    }
}
