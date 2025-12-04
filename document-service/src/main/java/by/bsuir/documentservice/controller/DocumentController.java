package by.bsuir.documentservice.controller;

import by.bsuir.documentservice.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(
        name = "Документы",
        description =
                "API для генерации складских документов: накладные, акты, описи и другие документы")
public class DocumentController {

    private final DocumentService documentService;

    @Operation(
            summary = "Получить документ",
            description = "Возвращает PDF-документ по его идентификатору"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Документ найден"),
            @ApiResponse(responseCode = "404", description = "Документ не найден")
    })
    @GetMapping("/{documentId}")
    public ResponseEntity<byte[]> getDocument(
            @Parameter(description = "ID документа", required = true) @PathVariable UUID documentId) {
        log.info("GET /api/documents/{}", documentId);

        byte[] document = documentService.getDocument(documentId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", documentId + ".pdf");

        return ResponseEntity.ok().headers(headers).body(document);
    }

    @Operation(
            summary = "Получить метаданные документа",
            description = "Возвращает метаданные документа: тип, дату создания, статус"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Метаданные получены"),
            @ApiResponse(responseCode = "404", description = "Документ не найден")
    })
    @GetMapping("/{documentId}/metadata")
    public ResponseEntity<Map<String, Object>> getDocumentMetadata(
            @Parameter(description = "ID документа", required = true) @PathVariable UUID documentId) {
        log.info("GET /api/documents/{}/metadata", documentId);

        Map<String, Object> metadata = documentService.getDocumentMetadata(documentId);
        return ResponseEntity.ok(metadata);
    }

    @Operation(
            summary = "Сгенерировать приходный ордер",
            description = "Создает приходный ордер для оформления поступления товаров на склад"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Документ успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные")
    })
    @PostMapping("/receipt-order")
    public ResponseEntity<Map<String, String>> generateReceiptOrder(
            @RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/receipt-order");

        UUID documentId = documentService.generateReceiptOrder(data);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        Map.of(
                                "documentId", documentId.toString(),
                                "type", "receipt-order",
                                "status", "stub",
                                "message",
                                        "Receipt order generation is not fully implemented yet"));
    }

    @Operation(
            summary = "Сгенерировать расходный ордер",
            description = "Создает расходный ордер для оформления отгрузки товаров со склада"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Документ успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные")
    })
    @PostMapping("/shipment-order")
    public ResponseEntity<Map<String, String>> generateShipmentOrder(
            @RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/shipment-order");

        UUID documentId = documentService.generateShipmentOrder(data);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        Map.of(
                                "documentId", documentId.toString(),
                                "type", "shipment-order",
                                "status", "stub",
                                "message",
                                        "Shipment order generation is not fully implemented yet"));
    }

    @Operation(
            summary = "Сгенерировать инвентаризационную опись",
            description = "Создает инвентаризационную опись для проведения инвентаризации"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Документ успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные")
    })
    @PostMapping("/inventory-report")
    public ResponseEntity<Map<String, String>> generateInventoryReport(
            @RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/inventory-report");

        UUID documentId = documentService.generateInventoryReport(data);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        Map.of(
                                "documentId", documentId.toString(),
                                "type", "inventory-report",
                                "status", "stub",
                                "message",
                                        "Inventory report generation is not fully implemented yet"));
    }

    @Operation(
            summary = "Сгенерировать акт переоценки",
            description = "Создает акт переоценки товарно-материальных ценностей"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Документ успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные")
    })
    @PostMapping("/revaluation-act")
    public ResponseEntity<Map<String, String>> generateRevaluationAct(
            @RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/revaluation-act");

        UUID documentId = documentService.generateRevaluationAct(data);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        Map.of(
                                "documentId", documentId.toString(),
                                "type", "revaluation-act",
                                "status", "stub",
                                "message",
                                        "Revaluation act generation is not fully implemented yet (RPA)"));
    }

    @Operation(
            summary = "Сгенерировать акт списания",
            description = "Создает акт списания товарно-материальных ценностей"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Документ успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные")
    })
    @PostMapping("/write-off-act")
    public ResponseEntity<Map<String, String>> generateWriteOffAct(
            @RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/write-off-act");

        UUID documentId = documentService.generateWriteOffAct(data);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        Map.of(
                                "documentId", documentId.toString(),
                                "type", "write-off-act",
                                "status", "stub",
                                "message",
                                        "Write-off act generation is not fully implemented yet"));
    }

    @Operation(
            summary = "Сгенерировать товарную накладную",
            description = "Создает товарную накладную для сопровождения груза"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Документ успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные")
    })
    @PostMapping("/waybill")
    public ResponseEntity<Map<String, String>> generateWaybill(
            @RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/waybill");

        UUID documentId = documentService.generateWaybill(data);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        Map.of(
                                "documentId", documentId.toString(),
                                "type", "waybill",
                                "status", "stub",
                                "message", "Waybill generation is not fully implemented yet"));
    }

    @Operation(
            summary = "Сгенерировать лист подбора",
            description = "Создает лист подбора товаров для комплектации заказа"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Документ успешно создан"),
            @ApiResponse(responseCode = "400", description = "Некорректные данные")
    })
    @PostMapping("/picking-list")
    public ResponseEntity<Map<String, String>> generatePickingList(
            @RequestBody Map<String, Object> data) {
        log.info("POST /api/documents/picking-list");

        UUID documentId = documentService.generatePickingList(data);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(
                        Map.of(
                                "documentId", documentId.toString(),
                                "type", "picking-list",
                                "status", "stub",
                                "message", "Picking list generation is not fully implemented yet"));
    }

    @Operation(
            summary = "Получить все документы",
            description = "Возвращает постраничный список всех документов в системе"
    )
    @ApiResponse(responseCode = "200", description = "Список документов получен")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllDocuments(
            @Parameter(description = "Номер страницы (начиная с 0)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Размер страницы") @RequestParam(defaultValue = "20") int size) {
        log.info("GET /api/documents?page={}&size={}", page, size);

        Map<String, Object> result = documentService.getAllDocuments(page, size);
        return ResponseEntity.ok(result);
    }

    @Operation(
            summary = "Получить информацию о сервисе",
            description = "Возвращает информацию о статусе сервиса документов и доступных эндпоинтах"
    )
    @ApiResponse(responseCode = "200", description = "Информация получена")
    @GetMapping("/stub-info")
    public ResponseEntity<Map<String, Object>> getStubInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "Document Service");
        info.put("status", "STUB");
        info.put("version", "0.1.0-SNAPSHOT");
        info.put("message", "Document Service is not fully implemented yet");
        info.put(
                "todo",
                new String[] {
                    "Implement PDF generation",
                    "Add RPA integration for automatic form filling",
                    "Create document templates",
                    "Add document storage (database or file system)",
                    "Implement electronic signature",
                    "Add document versioning",
                    "Implement document workflow (draft -> approved -> archived)"
                });
        info.put(
                "availableEndpoints",
                new String[] {
                    "GET /api/documents/{id}",
                    "GET /api/documents/{id}/metadata",
                    "GET /api/documents",
                    "POST /api/documents/receipt-order",
                    "POST /api/documents/shipment-order",
                    "POST /api/documents/inventory-report",
                    "POST /api/documents/revaluation-act",
                    "POST /api/documents/write-off-act",
                    "POST /api/documents/waybill",
                    "POST /api/documents/picking-list"
                });

        return ResponseEntity.ok(info);
    }
}
