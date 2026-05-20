package by.bsuir.documentservice.controller;

import by.bsuir.documentservice.rpa.PythonRpaClient;
import by.bsuir.documentservice.service.DocumentService;
import by.bsuir.documentservice.service.DocumentService.GenerationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Tag(name = "Документы")
public class DocumentController {

    private static final String MODE_HEADER = "X-Generation-Mode";
    private static final String CHANNEL_HEADER = "X-Generation-Channel";

    private final DocumentService documentService;
    private final PythonRpaClient pythonRpaClient;

    @PostMapping("/receipt-order")
    @Operation(summary = "Сгенерировать приходный ордер (bytes)")
    public ResponseEntity<byte[]> generateReceiptOrder(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return wrap(documentService.generate("receipt-order", data, organizationId, format, mode));
    }

    @PostMapping("/inventory-report")
    @Operation(summary = "Сгенерировать инвентаризационную опись (bytes)")
    public ResponseEntity<byte[]> generateInventoryReport(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return wrap(documentService.generate("inventory-report", data, organizationId, format, mode));
    }

    @PostMapping("/revaluation-act")
    @Operation(summary = "Сгенерировать акт переоценки (bytes)")
    public ResponseEntity<byte[]> generateRevaluationAct(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return wrap(documentService.generate("revaluation-act", data, organizationId, format, mode));
    }

    @PostMapping("/write-off-act")
    @Operation(summary = "Сгенерировать акт списания (bytes)")
    public ResponseEntity<byte[]> generateWriteOffAct(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return wrap(documentService.generate("write-off-act", data, organizationId, format, mode));
    }

    @PostMapping("/waybill")
    @Operation(summary = "Сгенерировать ТТН (bytes)")
    public ResponseEntity<byte[]> generateWaybill(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return wrap(documentService.generate("waybill", data, organizationId, format, mode));
    }

    @PostMapping("/picking-list")
    @Operation(summary = "Сгенерировать лист подбора (bytes)")
    public ResponseEntity<byte[]> generatePickingList(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return wrap(documentService.generate("picking-list", data, organizationId, format, mode));
    }

    @PostMapping("/placement-list")
    @Operation(summary = "Сгенерировать лист размещения (bytes)",
               description = "Список товаров с рекомендованными стеллажами/ячейками после приёмки")
    public ResponseEntity<byte[]> generatePlacementList(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return wrap(documentService.generate("placement-list", data, organizationId, format, mode));
    }

    @PostMapping("/receipt-act")
    @Operation(summary = "Сгенерировать акт приёмки (bytes)")
    public ResponseEntity<byte[]> generateReceiptAct(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return wrap(documentService.generate("receipt-act", data, organizationId, format, mode));
    }

    @PostMapping("/invoice")
    @Operation(summary = "Сгенерировать инвойс (bytes)")
    public ResponseEntity<byte[]> generateInvoice(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return wrap(documentService.generate("invoice", data, organizationId, format, mode));
    }

    @PostMapping("/transport-note")
    @Operation(summary = "Сгенерировать товарную накладную ТН (bytes). layout = horizontal | vertical")
    public ResponseEntity<byte[]> generateTransportNote(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestParam(required = false, defaultValue = "horizontal") String layout,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        data.putIfAbsent("layout", layout);
        return wrap(documentService.generate("transport-note", data, organizationId, format, mode));
    }

    @PostMapping("/cmr")
    @Operation(summary = "Сгенерировать CMR (bytes)")
    public ResponseEntity<byte[]> generateCmr(
            @RequestBody Map<String, Object> data,
            @RequestParam(required = false, defaultValue = "pdf") String format,
            @RequestHeader(value = MODE_HEADER, required = false, defaultValue = "auto") String mode,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        return wrap(documentService.generate("cmr", data, organizationId, format, mode));
    }

    @GetMapping("/rpa/health")
    @Operation(summary = "Здоровье Python rpa-service")
    public ResponseEntity<Map<String, Object>> getRpaHealth() {
        boolean available = pythonRpaClient.isAvailable();
        Map<String, Object> body = new HashMap<>();
        body.put("enabled", available);
        body.put("channel", "python");
        body.put("reason", available ? null
                : "Python rpa-service недоступен. Проверьте, что rpa-service запущен на хосте Windows и доступен по rpa.python.base-url.");
        return ResponseEntity.ok(body);
    }

    @GetMapping("/stub-info")
    public ResponseEntity<Map<String, Object>> getStubInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "Document Service");
        info.put("status", "active");
        info.put("version", "0.5.0-SNAPSHOT");
        info.put("mode", "stateless-bytes");
        info.put("documentTypes", new String[] {
                "receipt-order", "inventory-report",
                "revaluation-act", "write-off-act", "waybill", "picking-list",
                "receipt-act", "invoice", "transport-note", "cmr"
        });
        info.put("generationModes", new String[] {"auto (POI/PDFBox)", "rpa (Python service)"});
        info.put("formats", new String[] {"pdf (default)", "xlsx", "docx"});
        info.put("hint", "POST <type> возвращает bytes напрямую. Mode через header X-Generation-Mode. "
                + "RPA-канал требует rpa.python.enabled=true и работающий rpa-service на Windows-хосте.");
        return ResponseEntity.ok(info);
    }

    private ResponseEntity<byte[]> wrap(GenerationResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentTypeFor(result.format()));
        headers.set(CHANNEL_HEADER, result.channel());
        return ResponseEntity.ok().headers(headers).body(result.body());
    }

    private MediaType contentTypeFor(String format) {
        return switch (format) {
            case "xls" -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc" -> MediaType.parseMediaType("application/msword");
            case "rtf" -> MediaType.parseMediaType("application/rtf");
            default -> MediaType.APPLICATION_PDF;
        };
    }
}