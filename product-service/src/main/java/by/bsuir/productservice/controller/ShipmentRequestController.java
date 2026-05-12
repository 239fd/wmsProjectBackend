package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.CreateShipmentRequestRequest;
import by.bsuir.productservice.dto.request.PickRequest;
import by.bsuir.productservice.dto.response.ShipmentRequestResponse;
import by.bsuir.productservice.service.ShipmentRequestService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/operations/ship-requests")
@RequiredArgsConstructor
@Tag(name = "Заявки на отгрузку", description = "Управление заявками на отгрузку (FR-4): сборка, прогресс, документы")
public class ShipmentRequestController {

    private final ShipmentRequestService service;

    @Operation(summary = "Создать заявку на отгрузку")
    @PostMapping
    public ResponseEntity<ShipmentRequestResponse> create(
            @Valid @RequestBody CreateShipmentRequestRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, userId, organizationId));
    }

    @Operation(summary = "Список заявок")
    @GetMapping
    public ResponseEntity<List<ShipmentRequestResponse>> getAll(
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(service.getAll(organizationId));
    }

    @Operation(summary = "Получить заявку по ID")
    @GetMapping("/{requestId}")
    public ResponseEntity<ShipmentRequestResponse> get(
            @PathVariable UUID requestId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(service.get(requestId, organizationId));
    }

    @Operation(summary = "Отметить позицию как собранную (по штрихкоду/SKU). Идемпотентно")
    @PostMapping("/{requestId}/pick")
    public ResponseEntity<ShipmentRequestResponse> pick(
            @PathVariable UUID requestId,
            @Valid @RequestBody PickRequest pick,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(service.pick(requestId, pick, organizationId));
    }

    @Operation(summary = "Отменить отметку сборки (uncpick)")
    @PostMapping("/{requestId}/unpick")
    public ResponseEntity<ShipmentRequestResponse> unpick(
            @PathVariable UUID requestId,
            @Valid @RequestBody PickRequest pick,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(service.unpick(requestId, pick, organizationId));
    }

    @Operation(summary = "Завершить заявку",
            description = "Закрывает заявку и инициирует генерацию выбранных документов: отпускной ордер, ТТН, ТН, инвойс, CMR, счет-фактура")
    @PostMapping("/{requestId}/complete")
    public ResponseEntity<ShipmentRequestResponse> complete(
            @PathVariable UUID requestId,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        @SuppressWarnings("unchecked")
        List<String> documentTypes = (List<String>) body.getOrDefault("documentTypes", List.of());
        return ResponseEntity.ok(service.complete(requestId, documentTypes, organizationId));
    }

    @Operation(summary = "Отменить заявку")
    @DeleteMapping("/{requestId}")
    public ResponseEntity<Void> cancel(
            @PathVariable UUID requestId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        service.cancel(requestId, organizationId);
        return ResponseEntity.noContent().build();
    }
}
