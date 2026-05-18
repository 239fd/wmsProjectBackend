package by.bsuir.productservice.controller;

import by.bsuir.productservice.config.SecurityUtils;
import by.bsuir.productservice.service.ProductAnalyticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
@Tag(name = "Аналитика товаров", description = "API для получения аналитических данных о товарах и складских операциях")
public class ProductAnalyticsController {

    private final ProductAnalyticsService analyticsService;

    @Operation(summary = "Получить аналитику по остаткам", description = "Возвращает аналитические данные по текущим остаткам товаров на всех складах. Доступно только для DIRECTOR")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Аналитика успешно получена"), @ApiResponse(responseCode = "403", description = "Недостаточно прав")})
    @GetMapping("/inventory")
    public ResponseEntity<Map<String, Object>> getInventoryAnalytics(@Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        String role = SecurityUtils.resolveRole(userRole);
        if (!"DIRECTOR".equals(role) && !"ACCOUNTANT".equals(role)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> analytics = analyticsService.getInventoryAnalytics();
        return ResponseEntity.ok(analytics);
    }

    @Operation(summary = "Получить динамику операций", description = "Возвращает динамику складских операций за указанный период. Доступно только для DIRECTOR")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Динамика получена"), @ApiResponse(responseCode = "400", description = "Некорректный период"), @ApiResponse(responseCode = "403", description = "Недостаточно прав")})
    @GetMapping("/operations/dynamics")
    public ResponseEntity<Map<String, Object>> getOperationsDynamics(@Parameter(description = "Дата начала периода", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate, @Parameter(description = "Дата окончания периода", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate, @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        String role = SecurityUtils.resolveRole(userRole);
        if (!"DIRECTOR".equals(role) && !"ACCOUNTANT".equals(role)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> dynamics = analyticsService.getOperationsDynamics(startDate, endDate);
        return ResponseEntity.ok(dynamics);
    }

    @Operation(summary = "Получить сводку операций", description = "Возвращает сводку операций за последние 30 дней. Доступно только для DIRECTOR")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Сводка получена"), @ApiResponse(responseCode = "403", description = "Недостаточно прав")})
    @GetMapping("/operations/summary")
    public ResponseEntity<Map<String, Object>> getOperationsSummary(@Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        String role = SecurityUtils.resolveRole(userRole);
        if (!"DIRECTOR".equals(role) && !"ACCOUNTANT".equals(role)) {
            return ResponseEntity.status(403).build();
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(30);

        Map<String, Object> summary = analyticsService.getOperationsDynamics(startDate, endDate);
        return ResponseEntity.ok(summary);
    }

    @Operation(summary = "Сравнить операции с предыдущим периодом",
            description = "Считает суммы операций за заданный период [startDate, endDate] и за предыдущий равной длины. " +
                    "Используется для тренд-индикатора в KPI-карточках. Доступно только для DIRECTOR.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Сравнение получено"), @ApiResponse(responseCode = "403", description = "Недостаточно прав")})
    @GetMapping("/operations/compare")
    public ResponseEntity<Map<String, Object>> getOperationsComparison(
            @Parameter(description = "Дата начала текущего периода", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Дата окончания текущего периода", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        String role = SecurityUtils.resolveRole(userRole);
        if (!"DIRECTOR".equals(role) && !"ACCOUNTANT".equals(role)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> comparison = analyticsService.getOperationsComparison(startDate, endDate);
        return ResponseEntity.ok(comparison);
    }

    @Operation(summary = "Сравнить состояние запасов с началом периода",
            description = "Восстанавливает totalQuantity/availableQuantity на начало периода через сумму операций " +
                    "(receipt - ship - writeoff) и считает тренд. Доступно только для DIRECTOR.")
    @ApiResponses(value = {@ApiResponse(responseCode = "200", description = "Сравнение получено"), @ApiResponse(responseCode = "403", description = "Недостаточно прав")})
    @GetMapping("/inventory/compare")
    public ResponseEntity<Map<String, Object>> getInventoryComparison(
            @Parameter(description = "Дата начала периода", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "Дата окончания периода", required = true) @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        String role = SecurityUtils.resolveRole(userRole);
        if (!"DIRECTOR".equals(role) && !"ACCOUNTANT".equals(role)) {
            return ResponseEntity.status(403).build();
        }

        Map<String, Object> comparison = analyticsService.getInventoryComparison(startDate, endDate);
        return ResponseEntity.ok(comparison);
    }
}

