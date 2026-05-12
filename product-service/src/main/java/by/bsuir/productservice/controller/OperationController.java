package by.bsuir.productservice.controller;

import by.bsuir.productservice.client.DocumentClient;
import by.bsuir.productservice.dto.request.PlacementRequest;
import by.bsuir.productservice.dto.request.ReceiveProductRequest;
import by.bsuir.productservice.dto.request.RevaluationRequest;
import by.bsuir.productservice.dto.request.TransferProductRequest;
import by.bsuir.productservice.dto.request.WriteOffRequest;
import by.bsuir.productservice.dto.response.PlacementResponse;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import by.bsuir.productservice.service.BarcodeService;
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
    private final DocumentClient documentClient;
    private final ProductReadModelRepository productRepository;

    @Operation(
            summary = "Принять товар на склад",
            description = "Выполняет операцию приемки товара на склад с указанием партии и ячейки хранения"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Товар успешно принят"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные"),
            @ApiResponse(responseCode = "403", description = "Недостаточно прав")
    })
    @PostMapping("/receive")
    public ResponseEntity<Map<String, Object>> receiveProduct(
            @Valid @RequestBody ReceiveProductRequest request,
            @Parameter(description = "Роль пользователя") @RequestHeader(value = "X-User-Role", required = false) String userRole,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        UUID operationId = operationService.receiveProduct(request, organizationId);

        Map<String, Object> docPayload = new HashMap<>();
        docPayload.put("operationId", operationId.toString());
        docPayload.put("productId", request.productId().toString());
        docPayload.put("warehouseId", request.warehouseId().toString());
        docPayload.put("quantity", request.quantity());
        docPayload.put("userId", request.userId().toString());
        docPayload.put("date", LocalDate.now().toString());
        productRepository.findById(request.productId()).ifPresent(p -> {
            docPayload.put("productName", p.getName());
            docPayload.put("productSku", p.getSku());
        });
        UUID documentId = documentClient.generateReceiptOrder(docPayload, organizationId);

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Товар принят на склад");
        response.put("operationId", operationId);
        if (documentId != null) response.put("documentId", documentId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
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

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> result = revaluationService.revaluate(request, organizationId);

        Map<String, Object> docPayload = new HashMap<>(result);
        docPayload.put("date", LocalDate.now().toString());
        productRepository.findById(request.productId()).ifPresent(p -> {
            docPayload.put("productName", p.getName());
            docPayload.put("productSku", p.getSku());
        });
        UUID documentId = documentClient.generateRevaluationAct(docPayload, organizationId);
        if (documentId != null) result.put("documentId", documentId);

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

        if (userRole == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Map<String, Object> result = writeOffService.writeOff(request, organizationId);

        Map<String, Object> docPayload = new HashMap<>(result);
        docPayload.put("warehouseId", request.warehouseId().toString());
        docPayload.put("userId", request.userId().toString());
        docPayload.put("date", LocalDate.now().toString());
        productRepository.findById(request.productId()).ifPresent(p -> {
            docPayload.put("productName", p.getName());
            docPayload.put("productSku", p.getSku());
        });
        UUID documentId = documentClient.generateWriteOffAct(docPayload, organizationId);
        if (documentId != null) result.put("documentId", documentId);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @Operation(
            summary = "Список товаров, помеченных к списанию",
            description = "Возвращает позиции из инвентаризаций с marked_for_writeoff=true"
    )
    @GetMapping("/write-off/marked-items")
    public ResponseEntity<List<Map<String, Object>>> getMarkedItems(
            @org.springframework.web.bind.annotation.RequestParam(required = false) UUID warehouseId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        return ResponseEntity.ok(writeOffService.getMarkedItems(warehouseId, organizationId));
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