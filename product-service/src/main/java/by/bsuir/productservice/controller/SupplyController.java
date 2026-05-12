package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateSupplyRequest;
import by.bsuir.productservice.dto.response.SupplyResponse;
import by.bsuir.productservice.model.enums.SupplyStatus;
import by.bsuir.productservice.service.SupplyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/supplies")
@RequiredArgsConstructor
@Tag(name = "Поставки", description = "Управление поставками товаров: создание, отслеживание статуса, приёмка")
public class SupplyController {

    private final SupplyService supplyService;

    @Operation(summary = "Получить все поставки")
    @GetMapping
    public ResponseEntity<List<SupplyResponse>> getAll(
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) SupplyStatus status,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        if (warehouseId != null) {
            return ResponseEntity.ok(supplyService.getByWarehouse(warehouseId));
        }
        if (organizationId != null && status != null) {
            return ResponseEntity.ok(supplyService.getByOrganizationAndStatus(organizationId, status));
        }
        if (organizationId != null) {
            return ResponseEntity.ok(supplyService.getByOrganization(organizationId));
        }
        if (status != null) {
            return ResponseEntity.ok(supplyService.getByStatus(status));
        }
        return ResponseEntity.ok(supplyService.getAll());
    }

    @Operation(summary = "Получить поставку по ID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Поставка найдена"),
            @ApiResponse(responseCode = "404", description = "Поставка не найдена")
    })
    @GetMapping("/{supplyId}")
    public ResponseEntity<SupplyResponse> getById(@PathVariable UUID supplyId) {
        return ResponseEntity.ok(supplyService.getById(supplyId));
    }

    @Operation(summary = "Создать новую поставку",
            description = "Создаёт поставку в статусе PLANNED с позициями товаров")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Поставка создана"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные")
    })
    @PostMapping
    public ResponseEntity<SupplyResponse> create(
            @Valid @RequestBody CreateSupplyRequest request,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(supplyService.create(request, organizationId));
    }

    @Operation(summary = "Изменить статус поставки",
            description = "Допустимые переходы: PLANNED→IN_PROGRESS, PLANNED→CANCELLED, IN_PROGRESS→ACCEPTED, IN_PROGRESS→REJECTED")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Статус изменён"),
            @ApiResponse(responseCode = "400", description = "Недопустимый переход статуса"),
            @ApiResponse(responseCode = "404", description = "Поставка не найдена")
    })
    @PatchMapping("/{supplyId}/status")
    public ResponseEntity<SupplyResponse> updateStatus(
            @PathVariable UUID supplyId,
            @RequestBody Map<String, String> body) {

        SupplyStatus newStatus = SupplyStatus.valueOf(body.get("status"));
        UUID userId = body.get("userId") != null ? UUID.fromString(body.get("userId")) : null;
        return ResponseEntity.ok(supplyService.updateStatus(supplyId, newStatus, userId));
    }

    @Operation(summary = "Отменить поставку (только в статусе PLANNED)")
    @DeleteMapping("/{supplyId}")
    public ResponseEntity<Map<String, String>> cancel(@PathVariable UUID supplyId) {
        supplyService.delete(supplyId);
        return ResponseEntity.ok(Map.of("message", "Поставка отменена"));
    }
}