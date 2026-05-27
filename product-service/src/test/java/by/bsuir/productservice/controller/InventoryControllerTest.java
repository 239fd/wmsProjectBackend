package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.response.InventoryResponse;
import by.bsuir.productservice.model.entity.InventoryEvent;
import by.bsuir.productservice.repository.InventoryEventRepository;
import by.bsuir.productservice.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryController Tests")
class InventoryControllerTest {

    @Mock
    private InventoryService inventoryService;

    @Mock
    private InventoryEventRepository inventoryEventRepository;

    @InjectMocks
    private InventoryController controller;

    private InventoryResponse sample() {
        return new InventoryResponse(
                UUID.randomUUID(), UUID.randomUUID(), null, null, null,
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "INV-1",
                new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"),
                null, LocalDateTime.now());
    }

    @Test
    @DisplayName("getInventoryByWarehouse: возвращает Page<>")
    void getInventoryByWarehouse_givenWarehouseId_whenCalled_thenReturnsPage() {
        UUID wh = UUID.randomUUID();
        UUID org = UUID.randomUUID();
        when(inventoryService.getInventoryByWarehouse(eq(wh), eq(org), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        ResponseEntity<?> response = controller.getInventoryByWarehouse(wh, org, PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getInventoryByWarehouse: size > 100 ограничивается до 100")
    void getInventoryByWarehouse_givenLargePageSize_whenCalled_thenCaps() {
        UUID wh = UUID.randomUUID();
        when(inventoryService.getInventoryByWarehouse(any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        controller.getInventoryByWarehouse(wh, null, PageRequest.of(0, 500));

        var captor = org.mockito.ArgumentCaptor.forClass(Pageable.class);
        verify(inventoryService).getInventoryByWarehouse(any(), any(), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("getInventoryByProduct: возвращает Page<>")
    void getInventoryByProduct_givenProductId_whenCalled_thenReturnsPage() {
        UUID prod = UUID.randomUUID();
        when(inventoryService.getInventoryByProduct(eq(prod), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sample())));

        ResponseEntity<?> response = controller.getInventoryByProduct(prod, null, PageRequest.of(0, 20));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getInventoryByCell: 200 OK")
    void getInventoryByCell_givenCellId_whenCalled_thenReturns200() {
        UUID cell = UUID.randomUUID();
        when(inventoryService.getInventoryByCell(eq(cell), any())).thenReturn(sample());

        ResponseEntity<InventoryResponse> response = controller.getInventoryByCell(cell, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    @DisplayName("getHistory: маппит inventory_events в Map с eventId/eventType/eventData")
    void getHistory_givenInventoryId_whenCalled_thenMapsEvents() throws Exception {
        UUID invId = UUID.randomUUID();
        InventoryEvent event = InventoryEvent.builder()
                .eventId(123L)
                .inventoryId(invId)
                .eventType("ITEM_ADDED")
                .eventVersion(1)
                .eventData(new ObjectMapper().readTree("{\"qty\":10}"))
                .createdAt(LocalDateTime.now())
                .build();
        when(inventoryEventRepository.findByInventoryIdOrderByCreatedAtAsc(invId))
                .thenReturn(List.of(event));

        ResponseEntity<List<Map<String, Object>>> response = controller.getHistory(invId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);
        Map<String, Object> mapped = response.getBody().get(0);
        assertThat(mapped.get("eventId")).isEqualTo(123L);
        assertThat(mapped.get("eventType")).isEqualTo("ITEM_ADDED");
        assertThat(mapped.get("eventVersion")).isEqualTo(1);
        assertThat(mapped).containsKey("eventData");
        assertThat(mapped).containsKey("createdAt");
    }

    @Test
    @DisplayName("getHistory: пустая история → пустой список")
    void getHistory_givenNoEvents_whenCalled_thenReturnsEmptyList() {
        UUID invId = UUID.randomUUID();
        when(inventoryEventRepository.findByInventoryIdOrderByCreatedAtAsc(invId))
                .thenReturn(List.of());

        ResponseEntity<List<Map<String, Object>>> response = controller.getHistory(invId);

        assertThat(response.getBody()).isEmpty();
    }
}
