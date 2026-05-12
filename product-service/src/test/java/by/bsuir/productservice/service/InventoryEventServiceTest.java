package by.bsuir.productservice.service;

import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.InventoryEvent;
import by.bsuir.productservice.model.enums.InventoryEventType;
import by.bsuir.productservice.repository.InventoryEventRepository;
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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryEventService — модульные тесты")
class InventoryEventServiceTest {

    @Mock private InventoryEventRepository repository;
    @Spy  private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks private InventoryEventService service;

    private Inventory inventory(UUID id) {
        return Inventory.builder()
                .inventoryId(id)
                .productId(UUID.randomUUID())
                .warehouseId(UUID.randomUUID())
                .cellId(UUID.randomUUID())
                .quantity(new BigDecimal("100"))
                .build();
    }

    @Test
    @DisplayName("record: inventoryId=null → no-op без сохранения")
    void record_GivenNullInventoryId_ShouldSkip() {
        InventoryEvent result = service.record(null, InventoryEventType.ITEM_ADDED, Map.of("foo", "bar"));

        assertThat(result).isNull();
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("record: первое событие → version=1")
    void record_GivenFirstEvent_ShouldSetVersionOne() {
        UUID invId = UUID.randomUUID();
        when(repository.findMaxEventVersionByInventoryId(invId)).thenReturn(0);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryEvent result = service.record(invId, InventoryEventType.ITEM_ADDED,
                Map.of("quantity", "10"));

        assertThat(result.getEventVersion()).isEqualTo(1);
        assertThat(result.getEventType()).isEqualTo("ITEM_ADDED");
        assertThat(result.getInventoryId()).isEqualTo(invId);
    }

    @Test
    @DisplayName("record: с существующими событиями → version = max + 1")
    void record_GivenExistingEvents_ShouldIncrementVersion() {
        UUID invId = UUID.randomUUID();
        when(repository.findMaxEventVersionByInventoryId(invId)).thenReturn(5);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryEvent result = service.record(invId, InventoryEventType.WRITTEN_OFF,
                Map.of("reason", "expired"));

        assertThat(result.getEventVersion()).isEqualTo(6);
    }

    @Test
    @DisplayName("record: payload=null → пишет пустой JSON, не падает")
    void record_GivenNullPayload_ShouldNotFail() {
        UUID invId = UUID.randomUUID();
        when(repository.findMaxEventVersionByInventoryId(invId)).thenReturn(0);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryEvent result = service.record(invId, InventoryEventType.REVALUED, null);

        assertThat(result).isNotNull();
        assertThat(result.getEventData()).isNotNull();
        assertThat(result.getEventData().isObject()).isTrue();
    }

    @Test
    @DisplayName("recordQuantityChange: payload содержит quantityBefore/After/Delta + operationId/userId + extra")
    void recordQuantityChange_ShouldEnrichPayload() {
        UUID invId = UUID.randomUUID();
        Inventory inv = inventory(invId);
        UUID opId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(repository.findMaxEventVersionByInventoryId(invId)).thenReturn(0);

        ArgumentCaptor<InventoryEvent> captor = ArgumentCaptor.forClass(InventoryEvent.class);
        when(repository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        service.recordQuantityChange(inv, InventoryEventType.ITEM_REMOVED,
                new BigDecimal("100"), new BigDecimal("-25"),
                opId, userId, Map.of("source", "WRITE_OFF"));

        InventoryEvent saved = captor.getValue();
        assertThat(saved.getEventType()).isEqualTo("ITEM_REMOVED");
        assertThat(saved.getEventVersion()).isEqualTo(1);
        assertThat(saved.getEventData().get("quantityBefore").decimalValue()).isEqualByComparingTo("100");
        assertThat(saved.getEventData().get("quantityAfter").decimalValue()).isEqualByComparingTo("100");
        assertThat(saved.getEventData().get("quantityDelta").decimalValue()).isEqualByComparingTo("-25");
        assertThat(saved.getEventData().get("operationId").asText()).isEqualTo(opId.toString());
        assertThat(saved.getEventData().get("userId").asText()).isEqualTo(userId.toString());
        assertThat(saved.getEventData().get("source").asText()).isEqualTo("WRITE_OFF");
    }

    @Test
    @DisplayName("recordQuantityChange: extra=null → не падает, payload содержит базовые поля")
    void recordQuantityChange_GivenNoExtra_ShouldStillWork() {
        UUID invId = UUID.randomUUID();
        Inventory inv = inventory(invId);
        when(repository.findMaxEventVersionByInventoryId(invId)).thenReturn(0);
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        var result = service.recordQuantityChange(inv, InventoryEventType.ITEM_ADDED,
                BigDecimal.ZERO, new BigDecimal("10"), null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.getEventData().get("warehouseId").asText()).isEqualTo(inv.getWarehouseId().toString());
    }
}
