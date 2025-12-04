package by.bsuir.warehouseservice.controller;

import by.bsuir.warehouseservice.dto.request.CreateWarehouseRequest;
import by.bsuir.warehouseservice.dto.request.UpdateWarehouseRequest;
import by.bsuir.warehouseservice.dto.response.WarehouseResponse;
import by.bsuir.warehouseservice.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
@Tag(name = "Склады", description = "API для управления складами в системе WMS")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @Operation(
            summary = "Создать склад",
            description = "Создает новый склад в системе. Доступно для DIRECTOR и ACCOUNTANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Склад успешно создан",
                    content = @Content(schema = @Schema(implementation = WarehouseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping
    public ResponseEntity<WarehouseResponse> createWarehouse(
            @Valid @RequestBody CreateWarehouseRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        WarehouseResponse response = warehouseService.createWarehouse(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Получить склад по ID",
            description = "Возвращает информацию о складе по его уникальному идентификатору"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Склад найден",
                    content = @Content(schema = @Schema(implementation = WarehouseResponse.class))),
            @ApiResponse(responseCode = "404", description = "Склад не найден")
    })
    @GetMapping("/{warehouseId}")
    public ResponseEntity<WarehouseResponse> getWarehouse(
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId) {
        WarehouseResponse response = warehouseService.getWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить склады организации",
            description = "Возвращает список всех складов для указанной организации"
    )
    @ApiResponse(responseCode = "200", description = "Список складов получен")
    @GetMapping("/organization/{orgId}")
    public ResponseEntity<List<WarehouseResponse>> getWarehousesByOrganization(
            @Parameter(description = "ID организации", required = true) @PathVariable UUID orgId,
            @Parameter(description = "Только активные склады") @RequestParam(defaultValue = "false") boolean activeOnly) {

        List<WarehouseResponse> response = activeOnly
                ? warehouseService.getActiveWarehousesByOrganization(orgId)
                : warehouseService.getWarehousesByOrganization(orgId);

        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить все склады",
            description = "Возвращает список всех складов в системе"
    )
    @ApiResponse(responseCode = "200", description = "Список складов получен")
    @GetMapping
    public ResponseEntity<List<WarehouseResponse>> getAllWarehouses() {
        List<WarehouseResponse> response = warehouseService.getAllWarehouses();
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Обновить склад",
            description = "Обновляет информацию о складе. Доступно для DIRECTOR и ACCOUNTANT"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Склад обновлен",
                    content = @Content(schema = @Schema(implementation = WarehouseResponse.class))),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Склад не найден")
    })
    @PutMapping("/{warehouseId}")
    public ResponseEntity<WarehouseResponse> updateWarehouse(
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId,
            @Valid @RequestBody UpdateWarehouseRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader("X-User-Role") String userRole) {

        if (!"DIRECTOR".equals(userRole) && !"ACCOUNTANT".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        WarehouseResponse response = warehouseService.updateWarehouse(warehouseId, request);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Удалить склад",
            description = "Удаляет склад из системы. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Склад удален"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Склад не найден")
    })
    @DeleteMapping("/{warehouseId}")
    public ResponseEntity<Map<String, String>> deleteWarehouse(
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        warehouseService.deleteWarehouse(warehouseId);
        return ResponseEntity.ok(Map.of("message", "Склад успешно удалён"));
    }

    @Operation(
            summary = "Активировать склад",
            description = "Активирует склад для использования. Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Склад активирован",
                    content = @Content(schema = @Schema(implementation = WarehouseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Склад не найден")
    })
    @PostMapping("/{warehouseId}/activate")
    public ResponseEntity<WarehouseResponse> activateWarehouse(
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        WarehouseResponse response = warehouseService.activateWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Деактивировать склад",
            description = "Деактивирует склад (приостанавливает работу). Доступно только для DIRECTOR"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Склад деактивирован",
                    content = @Content(schema = @Schema(implementation = WarehouseResponse.class))),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав"),
            @ApiResponse(responseCode = "404", description = "Склад не найден")
    })
    @PostMapping("/{warehouseId}/deactivate")
    public ResponseEntity<WarehouseResponse> deactivateWarehouse(
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        if (userRole == null || !"DIRECTOR".equals(userRole)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        WarehouseResponse response = warehouseService.deactivateWarehouse(warehouseId);
        return ResponseEntity.ok(response);
    }

    @Operation(
            summary = "Получить информацию о складе",
            description = "Возвращает расширенную информацию о складе, включая статистику по стеллажам и ячейкам"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Информация получена"),
            @ApiResponse(responseCode = "404", description = "Склад не найден")
    })
    @GetMapping("/{warehouseId}/info")
    public ResponseEntity<Map<String, Object>> getWarehouseInfo(
            @Parameter(description = "ID склада", required = true) @PathVariable UUID warehouseId) {
        Map<String, Object> info = warehouseService.getWarehouseInfo(warehouseId);
        return ResponseEntity.ok(info);
    }
}
