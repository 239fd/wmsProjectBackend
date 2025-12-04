package by.bsuir.productservice.controller;

import by.bsuir.productservice.service.InventoryCheckService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/inventory-check")
@RequiredArgsConstructor
@Tag(name = "Инвентаризационные проверки", description = "API для проведения инвентаризации товаров на складе")
public class InventoryCheckController {

    private final InventoryCheckService inventoryCheckService;

    @Operation(
            summary = "Начать инвентаризацию",
            description = "Создает новую сессию инвентаризации для указанного склада. Доступно для DIRECTOR и ACCOUNTANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Инвентаризация начата"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Склад не найден")
    })
    @PostMapping("/start")
    public ResponseEntity<Map<String, String>> startInventory(
            @Parameter(description = "ID склада", required = true) @RequestParam UUID warehouseId,
            @Parameter(description = "ID пользователя", required = true) @RequestParam UUID userId,
            @Parameter(description = "Примечания") @RequestParam(required = false) String notes,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID sessionId = inventoryCheckService.startInventory(warehouseId, userId, notes);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "sessionId", sessionId.toString(),
                "message", "Инвентаризация начата",
                "warehouseId", warehouseId.toString()
        ));
    }

    @Operation(
            summary = "Зафиксировать фактическое количество",
            description = "Записывает фактическое количество товара в ходе инвентаризации"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Количество зафиксировано"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена")
    })
    @PostMapping("/{sessionId}/record")
    public ResponseEntity<Map<String, String>> recordActualCount(
            @Parameter(description = "ID сессии инвентаризации", required = true) @PathVariable UUID sessionId,
            @Parameter(description = "ID товара", required = true) @RequestParam UUID productId,
            @Parameter(description = "ID ячейки") @RequestParam(required = false) UUID cellId,
            @Parameter(description = "Фактическое количество", required = true) @RequestParam BigDecimal actualQuantity,
            @Parameter(description = "Примечания") @RequestParam(required = false) String notes,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        inventoryCheckService.recordActualCount(sessionId, productId, cellId, actualQuantity, notes);

        return ResponseEntity.ok(Map.of(
                "message", "Фактическое количество зафиксировано",
                "sessionId", sessionId.toString(),
                "productId", productId.toString(),
                "actualQuantity", actualQuantity.toString()
        ));
    }

    @Operation(
            summary = "Завершить инвентаризацию",
            description = "Завершает сессию инвентаризации и формирует отчет о расхождениях. Доступно для DIRECTOR и ACCOUNTANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Инвентаризация завершена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена")
    })
    @PostMapping("/{sessionId}/complete")
    public ResponseEntity<Map<String, Object>> completeInventory(
            @Parameter(description = "ID сессии инвентаризации", required = true) @PathVariable UUID sessionId,
            @Parameter(description = "ID пользователя", required = true) @RequestParam UUID userId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> result = inventoryCheckService.completeInventory(sessionId, userId);

        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Отменить инвентаризацию",
            description = "Отменяет текущую сессию инвентаризации. Доступно для DIRECTOR и ACCOUNTANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Инвентаризация отменена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена")
    })
    @PostMapping("/{sessionId}/cancel")
    public ResponseEntity<Map<String, String>> cancelInventory(
            @Parameter(description = "ID сессии инвентаризации", required = true) @PathVariable UUID sessionId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        inventoryCheckService.cancelInventory(sessionId);

        return ResponseEntity.ok(Map.of(
                "message", "Инвентаризация отменена",
                "sessionId", sessionId.toString()
        ));
    }

    @Operation(
            summary = "Получить сессию инвентаризации",
            description = "Возвращает информацию о сессии инвентаризации и её текущий статус"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Сессия найдена"),
            @ApiResponse(responseCode = "404", description = "Сессия не найдена")
    })
    @GetMapping("/{sessionId}")
    public ResponseEntity<Map<String, Object>> getInventorySession(
            @Parameter(description = "ID сессии инвентаризации", required = true) @PathVariable UUID sessionId) {
        Map<String, Object> session = inventoryCheckService.getInventorySession(sessionId);
        return ResponseEntity.ok(session);
    }
}
