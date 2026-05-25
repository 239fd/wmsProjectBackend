package by.bsuir.productservice.controller;

import by.bsuir.productservice.config.SecurityUtils;
import by.bsuir.productservice.dto.request.DiscrepancyRequest;
import by.bsuir.productservice.dto.request.PlacementRequest;
import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.RevaluationRequest;
import by.bsuir.productservice.dto.request.TransferProductRequest;
import by.bsuir.productservice.dto.request.WriteOffRequest;
import by.bsuir.productservice.dto.response.PlacementResponse;
import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.GeneratedDocument;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.enums.OperationStatus;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import by.bsuir.productservice.service.BarcodeService;
import by.bsuir.productservice.service.DocumentRegistryService;
import by.bsuir.productservice.service.PlacementService;
import by.bsuir.productservice.service.ProductOperationService;
import by.bsuir.productservice.service.RevaluationService;
import by.bsuir.productservice.service.WriteOffService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/operations")
@RequiredArgsConstructor
@Tag(name = "Складские операции", description = "API для управления складскими операциями: приемка, отгрузка, резервирование товаров")
public class OperationController {

    private final ProductOperationService operationService;
    private final PlacementService placementService;
    private final BarcodeService barcodeService;
    private final RevaluationService revaluationService;
    private final WriteOffService writeOffService;
    private final DocumentRegistryService documentRegistryService;
    private final ProductReadModelRepository productRepository;
    private final ProductOperationRepository productOperationRepository;
    private final by.bsuir.productservice.repository.InventoryRepository inventoryRepository;
    private final by.bsuir.productservice.repository.ProductBatchRepository batchRepository;
    private final by.bsuir.productservice.client.WarehouseClient warehouseClient;

    private GeneratedDocument safeRegister(
            UUID operationId,
            String type,
            Map<String, Object> payload,
            UUID organizationId,
            UUID userId) {
        try {
            return documentRegistryService.register(operationId, type, payload, organizationId, userId);
        } catch (Exception e) {
            log.error("Не удалось зарегистрировать документ типа {} для операции {} (org={}): {}",
                    type, operationId, organizationId, e.getMessage(), e);
            return null;
        }
    }

    private void enrichProduct(Map<String, Object> payload, UUID productId, UUID organizationId) {
        var lookup = (organizationId != null)
                ? productRepository.findByProductIdAndOrganizationId(productId, organizationId)
                : productRepository.findById(productId);
        lookup.ifPresent(p -> {
            payload.put("productName", p.getName());
            payload.put("productSku", p.getSku());
        });
    }

    @Operation(
            summary = "Переместить товар (transfer)",
            description = "Перемещает товар между ячейками/складами. SKU перевыпускается у назначения (ANS6)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Товар перемещён"),
            @ApiResponse(responseCode = "400", description = "Недостаточно товара или некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping("/transfer")
    public ResponseEntity<Map<String, Object>> transferProduct(
            @Valid @RequestBody TransferProductRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID operationId = operationService.transferProduct(request, organizationId);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "message", "Товар перемещён",
                "operationId", operationId
        ));
    }

    @Operation(
            summary = "История операций (paginated, с фильтрами)",
            description = "Возвращает страницу операций с фильтрами: type, warehouseId, userId, productId, startDate, endDate. "
                    + "Каждая операция enriched полями productName, sku из read-model.")
    @GetMapping("/history")
    public ResponseEntity<Page<Map<String, Object>>> getOperationsHistory(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) UUID warehouseId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate startDate,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            java.time.LocalDate endDate,
            @PageableDefault(size = 20, sort = "operationDate", direction = Sort.Direction.DESC) Pageable pageable,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        by.bsuir.productservice.model.enums.OperationType typeEnum = parseOperationType(type);
        java.time.LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        java.time.LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : null;
        Pageable effective = normalizeSort(pageable);
        Page<ProductOperation> page =
                productOperationRepository.searchHistory(
                        organizationId, typeEnum, warehouseId, userId, productId, start, end, effective);

        java.util.Set<UUID> productIds = page.getContent().stream()
                .map(ProductOperation::getProductId)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        java.util.Map<UUID, by.bsuir.productservice.model.entity.ProductReadModel> products =
                productRepository.findAllById(productIds).stream()
                        .collect(java.util.stream.Collectors.toMap(
                                by.bsuir.productservice.model.entity.ProductReadModel::getProductId,
                                p -> p));

        return ResponseEntity.ok(page.map(op -> {
            Map<String, Object> row = new HashMap<>();
            row.put("operationId", op.getOperationId());
            row.put("operationType", op.getOperationType());
            row.put("operationDate", op.getOperationDate());
            row.put("productId", op.getProductId());
            row.put("batchId", op.getBatchId());
            row.put("warehouseId", op.getWarehouseId());
            row.put("fromCellId", op.getFromCellId());
            row.put("toCellId", op.getToCellId());
            row.put("quantity", op.getQuantity());
            row.put("userId", op.getUserId());
            row.put("documentId", op.getDocumentId());
            row.put("status", op.getStatus());
            row.put("notes", op.getNotes());
            by.bsuir.productservice.model.entity.ProductReadModel p = products.get(op.getProductId());
            if (p != null) {
                row.put("productName", p.getName());
                row.put("sku", p.getSku());
                row.put("unitOfMeasure", p.getUnitOfMeasure());
            }
            return row;
        }));
    }

    private Pageable normalizeSort(Pageable pageable) {
        Sort sort = pageable.getSort();
        Sort mapped = Sort.by(sort.stream().map(o -> {
            String prop = switch (o.getProperty()) {
                case "createdAt", "timestamp" -> "operationDate";
                default -> o.getProperty();
            };
            return new Sort.Order(o.getDirection(), prop);
        }).toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), mapped);
    }

    private by.bsuir.productservice.model.enums.OperationType parseOperationType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String normalized = switch (raw.toUpperCase()) {
            case "RECEIVE" -> "RECEIPT";
            case "SHIP", "SHIPPING" -> "SHIPMENT";
            case "WRITEOFF" -> "WRITE_OFF";
            default -> raw.toUpperCase();
        };
        try {
            return by.bsuir.productservice.model.enums.OperationType.valueOf(normalized);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown OperationType: {}", raw);
            return null;
        }
    }

    @Operation(
            summary = "Авто-размещение партии",
            description = "Подбирает свободную ячейку с подходящими условиями хранения (учёт ABC-класса) и размещает партию"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Партия размещена"),
            @ApiResponse(responseCode = "404", description = "Партия не найдена"),
            @ApiResponse(responseCode = "409", description = "Нет свободной ячейки с подходящими условиями")
    })
    @PostMapping("/placement/auto")
    public ResponseEntity<PlacementResponse> autoPlacement(
            @Valid @RequestBody PlacementRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        PlacementResponse response = placementService.autoPlacement(request, organizationId, userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Ручное размещение партии",
            description = "Размещает партию в указанной ячейке (валидируются условия хранения и занятость)"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Партия размещена"),
            @ApiResponse(responseCode = "404", description = "Партия или ячейка не найдена"),
            @ApiResponse(responseCode = "409", description = "Условия хранения не совпадают или ячейка занята")
    })
    @PostMapping("/placement/manual")
    public ResponseEntity<PlacementResponse> manualPlacement(
            @Valid @RequestBody PlacementRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        PlacementResponse response = placementService.manualPlacement(request, organizationId, userRole);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Operation(
            summary = "Сгенерировать SKU для запаса",
            description = "Присваивает уникальный SKU единице запаса на основе организации, склада, стеллажа и ячейки"
    )
    @PostMapping("/barcodes/{inventoryId}/generate")
    public ResponseEntity<Map<String, String>> generateSku(
            @PathVariable UUID inventoryId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        String sku = barcodeService.assignSkuToInventory(inventoryId, userRole);
        return ResponseEntity.ok(Map.of("inventoryId", inventoryId.toString(), "sku", sku));
    }

    @Operation(
            summary = "Переоценка товара",
            description = "Меняет учётную цену товара. Требует причину, основание, ответственного и состав комиссии"
    )
    @PostMapping("/revaluate")
    public ResponseEntity<Map<String, Object>> revaluate(
            @Valid @RequestBody RevaluationRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> result = revaluationService.revaluate(request, organizationId);

        Map<String, Object> docPayload = new HashMap<>(result);
        docPayload.put("date", LocalDate.now().toString());
        if (request.warehouseId() != null) {
            docPayload.put("warehouseId", request.warehouseId().toString());
        }
        enrichProduct(docPayload, request.productId(), organizationId);
        UUID operationId = result.get("operationId") instanceof UUID id ? id : null;
        GeneratedDocument document = safeRegister(
                operationId, "revaluation-act", docPayload, organizationId, request.userId());
        if (document != null) {
            result.put("documentId", document.getId());
            result.put("documentNumber", document.getDocumentNumber());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(
            summary = "Списание товара",
            description = "Списывает указанное количество товара с указанием причины, основания, ответственного и комиссии"
    )
    @PostMapping("/write-off")
    public ResponseEntity<Map<String, Object>> writeOff(
            @Valid @RequestBody WriteOffRequest request,
            @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        userRole = SecurityUtils.resolveRole(userRole);
        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> result = writeOffService.writeOff(request, organizationId);

        Map<String, Object> docPayload = new HashMap<>(result);
        docPayload.put("warehouseId", request.warehouseId().toString());
        docPayload.put("userId", request.userId().toString());
        docPayload.put("date", LocalDate.now().toString());
        enrichProduct(docPayload, request.productId(), organizationId);
        UUID operationId = result.get("operationId") instanceof UUID id ? id : null;
        GeneratedDocument document = safeRegister(
                operationId, "write-off-act", docPayload, organizationId, request.userId());
        if (document != null) {
            result.put("documentId", document.getId());
            result.put("documentNumber", document.getDocumentNumber());
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    private static final int MAX_PAGE_SIZE = 100;

    @Operation(
            summary = "Список товаров, помеченных к списанию (пагинация)",
            description = "Возвращает страницу позиций из инвентаризаций с marked_for_writeoff=true"
    )
    @GetMapping("/write-off/marked-items")
    public ResponseEntity<Page<Map<String, Object>>> getMarkedItems(
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID warehouseId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @PageableDefault(size = 20, sort = "countId", direction = Sort.Direction.DESC) Pageable pageable) {

        return ResponseEntity.ok(writeOffService.getMarkedItems(warehouseId, organizationId, capSize(pageable)));
    }

    private static Pageable capSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    @Operation(
            summary = "Печать листа штрихкодов",
            description = "Возвращает PDF со штрихкодами EAN-13 для указанных inventoryIds"
    )
    @PostMapping(value = "/barcodes/print", produces = "application/pdf")
    public ResponseEntity<byte[]> printBarcodes(
            @RequestBody List<UUID> inventoryIds,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        byte[] pdf = barcodeService.generateBarcodeSheetPdf(inventoryIds, organizationId);
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=barcodes.pdf")
                .body(pdf);
    }
}