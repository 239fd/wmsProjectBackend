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
@Tag(name = "Документы",
        description = "API для генерации складских документов: накладные, акты, описи и другие документы")
public class DocumentController {

    private final DocumentService documentService;

    @Operation(summary = "Получить документ", description = "Регенерирует PDF по сохранённым метаданным")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Документ найден"),
            @ApiResponse(responseCode = "404", description = "Документ не найден"),
            @ApiResponse(responseCode = "403", description = "Документ принадлежит другой организации")
    })
    @GetMapping("/{documentId}")
    public ResponseEntity<byte[]> getDocument(
            @Parameter(description = "ID документа", required = true) @PathVariable UUID documentId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        byte[] document = documentService.getDocument(documentId, organizationId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", documentId + ".pdf");
        return ResponseEntity.ok().headers(headers).body(document);
    }

    @Operation(summary = "Получить метаданные документа")
    @GetMapping("/{documentId}/metadata")
    public ResponseEntity<Map<String, Object>> getDocumentMetadata(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(documentService.getDocumentMetadata(documentId, organizationId));
    }

    @PostMapping("/receipt-order")
    @Operation(summary = "Сгенерировать приходный ордер")
    public ResponseEntity<Map<String, String>> generateReceiptOrder(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateReceiptOrder(data, organizationId, userId, format);
        return created(id, "receipt-order");
    }

    @PostMapping("/release-order")
    @Operation(summary = "Сгенерировать отпускной ордер")
    public ResponseEntity<Map<String, String>> generateReleaseOrder(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateShipmentOrder(data, organizationId, userId, format);
        return created(id, "release-order");
    }

    @PostMapping("/shipment-order")
    @Operation(summary = "Сгенерировать расходный ордер (alias for release-order)")
    public ResponseEntity<Map<String, String>> generateShipmentOrder(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateShipmentOrder(data, organizationId, userId, format);
        return created(id, "release-order");
    }

    @PostMapping("/inventory-report")
    @Operation(summary = "Сгенерировать инвентаризационную опись")
    public ResponseEntity<Map<String, String>> generateInventoryReport(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateInventoryReport(data, organizationId, userId, format);
        return created(id, "inventory-report");
    }

    @PostMapping("/revaluation-act")
    @Operation(summary = "Сгенерировать акт переоценки")
    public ResponseEntity<Map<String, String>> generateRevaluationAct(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateRevaluationAct(data, organizationId, userId, format);
        return created(id, "revaluation-act");
    }

    @PostMapping("/write-off-act")
    @Operation(summary = "Сгенерировать акт списания")
    public ResponseEntity<Map<String, String>> generateWriteOffAct(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateWriteOffAct(data, organizationId, userId, format);
        return created(id, "write-off-act");
    }

    @PostMapping("/waybill")
    @Operation(summary = "Сгенерировать ТТН")
    public ResponseEntity<Map<String, String>> generateWaybill(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateWaybill(data, organizationId, userId, format);
        return created(id, "waybill");
    }

    @PostMapping("/picking-list")
    @Operation(summary = "Сгенерировать лист подбора")
    public ResponseEntity<Map<String, String>> generatePickingList(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generatePickingList(data, organizationId, userId, format);
        return created(id, "picking-list");
    }

    @PostMapping("/receipt-act")
    @Operation(summary = "Сгенерировать акт приёмки товара")
    public ResponseEntity<Map<String, String>> generateReceiptAct(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateReceiptAct(data, organizationId, userId, format);
        return created(id, "receipt-act");
    }

    @PostMapping("/invoice-fact")
    @Operation(summary = "Сгенерировать счёт-фактуру")
    public ResponseEntity<Map<String, String>> generateInvoiceFact(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateInvoiceFact(data, organizationId, userId, format);
        return created(id, "invoice-fact");
    }

    @PostMapping("/invoice")
    @Operation(summary = "Сгенерировать инвойс")
    public ResponseEntity<Map<String, String>> generateInvoice(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateInvoice(data, organizationId, userId, format);
        return created(id, "invoice");
    }

    @PostMapping("/transport-note")
    @Operation(summary = "Сгенерировать товарную накладную (ТН)")
    public ResponseEntity<Map<String, String>> generateTransportNote(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateTransportNote(data, organizationId, userId, format);
        return created(id, "transport-note");
    }

    @PostMapping("/cmr")
    @Operation(summary = "Сгенерировать CMR")
    public ResponseEntity<Map<String, String>> generateCmr(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateCmr(data, organizationId, userId, format);
        return created(id, "cmr");
    }

    @PostMapping("/discrepancy-act")
    @Operation(summary = "Сгенерировать акт о расхождении")
    public ResponseEntity<Map<String, String>> generateDiscrepancyAct(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {
        UUID id = documentService.generateDiscrepancyAct(data, organizationId, userId, format);
        return created(id, "discrepancy-act");
    }

    @Operation(summary = "Получить все документы (фильтр по организации)")
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllDocuments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return ResponseEntity.ok(documentService.getAllDocuments(page, size, organizationId));
    }

    @GetMapping("/stub-info")
    public ResponseEntity<Map<String, Object>> getStubInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "Document Service");
        info.put("status", "active");
        info.put("version", "0.2.0-SNAPSHOT");
        info.put("documentTypes", new String[] {
                "receipt-order", "release-order", "shipment-order", "inventory-report",
                "revaluation-act", "write-off-act", "waybill", "picking-list",
                "receipt-act", "invoice-fact", "invoice", "transport-note", "cmr", "discrepancy-act"
        });
        info.put("formats", new String[] {"pdf (default)", "rpa-xls", "rpa-docx"});
        return ResponseEntity.ok(info);
    }

    private ResponseEntity<Map<String, String>> created(UUID id, String type) {
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "documentId", id.toString(),
                "type", type,
                "status", "generated"));
    }
}
