package by.bsuir.warehouseservice.controller;

import by.bsuir.warehouseservice.service.WarehouseAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/warehouses/analytics")
@RequiredArgsConstructor
@Tag(name = "Аналитика складов", description = "API для получения аналитических данных о складах и их использовании")
public class WarehouseAnalyticsController {

    private final WarehouseAnalyticsService analyticsService;

    @Operation(
            summary = "Получить аналитику по складу",
            description = "Возвращает аналитические данные по конкретному складу: заполненность, количество товаров, активность. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Аналитика успешно получена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Склад не найден")
    })
    @GetMapping("/{warehouseId}")
    public ResponseEntity<Map<String, Object>> getWarehouseAnalytics(
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> analytics = analyticsService.getWarehouseAnalytics(warehouseId);
        return ResponseEntity.ok(analytics);
    }

    @Operation(
            summary = "Получить сводку по складам организации",
            description = "Возвращает сводную аналитику по всем складам организации. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Сводка успешно получена"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Организация не найдена")
    })
    @GetMapping("/organization/{orgId}/summary")
    public ResponseEntity<Map<String, Object>> getOrganizationWarehousesSummary(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (!"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> summary = analyticsService.getOrganizationWarehousesSummary(orgId);
        return ResponseEntity.ok(summary);
    }
}

