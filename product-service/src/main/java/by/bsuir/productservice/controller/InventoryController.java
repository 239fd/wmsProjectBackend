package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.response.InventoryResponse;
import by.bsuir.productservice.model.entity.InventoryEvent;
import by.bsuir.productservice.repository.InventoryEventRepository;
import by.bsuir.productservice.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@Tag(name = "Остатки товаров", description = "API для получения информации об остатках товаров на складах и в ячейках хранения")
public class InventoryController {

    private final InventoryService inventoryService;
    private final InventoryEventRepository inventoryEventRepository;

    private static final int MAX_PAGE_SIZE = 100;

    @Operation(
            summary = "Получить остатки по складу (пагинация)",
            description = "Возвращает страницу остатков на указанном складе"
    )
    @ApiResponse(responseCode = "200", description = "Остатки успешно получены")
    @GetMapping("/warehouse/{warehouseId}")
    public ResponseEntity<Page<InventoryResponse>> getInventoryByWarehouse(
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PageableDefault(size = 20, sort = "lastUpdated", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getInventoryByWarehouse(warehouseId, organizationId, capSize(pageable)));
    }

    @Operation(
            summary = "Получить остатки товара (пагинация)",
            description = "Возвращает страницу остатков конкретного товара по складам"
    )
    @ApiResponse(responseCode = "200", description = "Остатки успешно получены")
    @GetMapping("/product/{productId}")
    public ResponseEntity<Page<InventoryResponse>> getInventoryByProduct(
            @Parameter(description = "ID товара", required = true) @PathVariable UUID productId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PageableDefault(size = 20, sort = "lastUpdated", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(inventoryService.getInventoryByProduct(productId, organizationId, capSize(pageable)));
    }

    private static Pageable capSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    @Operation(
            summary = "Получить остатки в ячейке",
            description = "Возвращает информацию об остатках в конкретной ячейке хранения"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Остатки получены"),
            @ApiResponse(responseCode = "404", description = "Ячейка не найдена или пуста")
    })
    @GetMapping("/cell/{cellId}")
    public ResponseEntity<InventoryResponse> getInventoryByCell(
            @Parameter(description = "ID ячейки", required = true) @PathVariable UUID cellId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        InventoryResponse response = inventoryService.getInventoryByCell(cellId, organizationId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "История изменений остатка (event-store)",
            description = "Возвращает все доменные события `inventory_events` для конкретного inventoryId. "
                    + "Источник аудит-trail: ITEM_ADDED, ITEM_REMOVED, REVALUED, WRITTEN_OFF + компенсации саги."
    )
    @ApiResponse(responseCode = "200", description = "История получена")
    @GetMapping("/{inventoryId}/history")
    public ResponseEntity<List<Map<String, Object>>> getHistory(
            @Parameter(description = "ID inventory-записи", required = true) @PathVariable UUID inventoryId) {
        List<InventoryEvent> events = inventoryEventRepository.findByInventoryIdOrderByCreatedAtAsc(inventoryId);
        List<Map<String, Object>> response = events.stream().map(e -> {
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("eventId", e.getEventId());
            m.put("eventType", e.getEventType());
            m.put("eventVersion", e.getEventVersion());
            m.put("eventData", e.getEventData());
            m.put("createdAt", e.getCreatedAt());
            return m;
        }).collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }
}
