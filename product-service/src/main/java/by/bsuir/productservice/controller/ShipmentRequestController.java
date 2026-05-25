package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.import_.SalesOrderDto;
import by.bsuir.productservice.dto.request.AddShipmentItemsRequest;
import by.bsuir.productservice.dto.request.CompleteShipmentRequest;
import by.bsuir.productservice.dto.request.CreateShipmentRequestRequest;
import by.bsuir.productservice.dto.request.PickRequest;
import by.bsuir.productservice.dto.response.ShipmentRequestResponse;
import by.bsuir.productservice.rpa.PythonRpaSalesExtractor;
import by.bsuir.productservice.service.SalesImportService;
import by.bsuir.productservice.service.ShipmentRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
    private final SalesImportService salesImportService;
    private final ObjectProvider<PythonRpaSalesExtractor> salesExtractorProvider;

    private static final int MAX_PAGE_SIZE = 100;

    @Operation(summary = "Импорт отгрузок из 1С (Заказы клиентов через Python RPA)")
    @PostMapping("/import-1c")
    public ResponseEntity<Map<String, Object>> importFrom1c(
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-Warehouse-Id", required = false) UUID warehouseId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        PythonRpaSalesExtractor extractor = salesExtractorProvider.getIfAvailable();
        if (extractor == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "RPA-канал отключён (rpa.python.enabled=false)"));
        }
        List<SalesOrderDto> orders = extractor.extractSales();
        SalesImportService.ImportResult result =
                salesImportService.importSales(organizationId, warehouseId, userId, orders);
        return ResponseEntity.ok(result.toMap());
    }

    @Operation(summary = "Создать заявку на отгрузку")
    @PostMapping
    public ResponseEntity<ShipmentRequestResponse> create(
            @Valid @RequestBody CreateShipmentRequestRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request, userId, organizationId));
    }

    @Operation(summary = "Список заявок (пагинация)")
    @GetMapping
    public ResponseEntity<Page<ShipmentRequestResponse>> getAll(
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(service.getAll(organizationId, capSize(pageable)));
    }

    private static Pageable capSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    @Operation(summary = "Получить заявку по ID")
    @GetMapping("/{requestId}")
    public ResponseEntity<ShipmentRequestResponse> get(
            @PathVariable UUID requestId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(service.get(requestId, organizationId));
    }

    @Operation(summary = "Добавить позиции в существующую заявку",
            description = "Резервирует партии под новые позиции (стратегия из заявки) и добавляет их. "
                    + "Доступно только для заявок в статусе PLANNED/PICKING.")
    @PostMapping("/{requestId}/items")
    public ResponseEntity<ShipmentRequestResponse> addItems(
            @PathVariable UUID requestId,
            @Valid @RequestBody AddShipmentItemsRequest request,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(service.addItems(requestId, request, userId, organizationId));
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
            description = "Закрывает заявку и генерирует пакет документов по выбранному типу отгрузки: "
                    + "DOMESTIC → ТН/ТТН по выбранному kind+layout; EXPORT → пакет {ТН + CMR + invoice}. "
                    + "Тело запроса опционально — содержит транспортные/доверенность/контракт/перевозчик-поля, "
                    + "которые попадают только в payload документа, в БД не сохраняются.")
    @PostMapping("/{requestId}/complete")
    public ResponseEntity<ShipmentRequestResponse> complete(
            @PathVariable UUID requestId,
            @RequestBody(required = false) CompleteShipmentRequest manual,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(service.complete(requestId, userId, organizationId, manual));
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
