package by.bsuir.documentservice.controller;

import by.bsuir.documentservice.config.RpaProperties;
import by.bsuir.documentservice.dto.OfficeFillRequest;
import by.bsuir.documentservice.rpa.OfficeDocumentBot;
import by.bsuir.documentservice.service.DocumentService;
import by.bsuir.documentservice.service.DocumentService.GenerationResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
@Tag(name = "Документы",
        description = "Stateless генератор: POST <type> возвращает PDF/XLS/DOCX bytes напрямую. "
                + "Хранением занимается product-service (DocumentRegistryService + MinIO). "
                + "Канал генерации выбирается через header X-Generation-Mode: auto (default) | rpa.")
public class DocumentController {

    private static final String MODE_HEADER = "X-Generation-Mode";
    private static final String CHANNEL_HEADER = "X-Generation-Channel";

    private final DocumentService documentService;
    private final ObjectProvider<OfficeDocumentBot> officeBotProvider;
    private final RpaProperties rpaProperties;

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

    @GetMapping("/office/health")
    @Operation(summary = "Готовность RPA-канала (OfficeDocumentBot)")
    public ResponseEntity<Map<String, Object>> officeHealth() {
        boolean enabled = officeBotProvider.getIfAvailable() != null;
        Map<String, Object> body = new HashMap<>();
        body.put("enabled", enabled);
        body.put("reason", enabled ? "ok" : "rpa.office.enabled=false or bot not wired");
        return ResponseEntity.ok(body);
    }

    @PostMapping("/office/fill")
    @Operation(summary = "RPA-2: заполнить локальный шаблон MS Office (Excel/Word) через WinAppDriver",
            description = "Прямой эндпоинт RPA-бота (без бизнес-маппинга). Принимает templateName + cells/placeholders, "
                    + "возвращает заполненный файл. Включается через rpa.office.enabled=true.")
    public ResponseEntity<byte[]> fillOfficeTemplate(@Valid @RequestBody OfficeFillRequest request) {
        OfficeDocumentBot bot = officeBotProvider.getIfAvailable();
        if (bot == null) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(("OfficeDocumentBot не включён: установите rpa.office.enabled=true "
                            + "и запустите WinAppDriver").getBytes());
        }

        Path templatePath = resolveTemplate(request.templateName());
        if (!Files.exists(templatePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(("Шаблон не найден: " + request.templateName()).getBytes());
        }

        try {
            Path output;
            String lower = request.templateName().toLowerCase();
            boolean isWord = lower.endsWith(".doc") || lower.endsWith(".docx")
                    || lower.endsWith(".rtf");
            String outputName = request.outputName() != null && !request.outputName().isBlank()
                    ? request.outputName()
                    : request.templateName().replaceFirst("\\.[^.]+$", "");
            if (isWord) {
                output = bot.fillWordTemplate(templatePath,
                        request.placeholders() != null ? request.placeholders() : Map.of(),
                        outputName);
            } else {
                output = bot.fillExcelTemplate(templatePath,
                        request.cells() != null ? request.cells() : Map.of(),
                        outputName);
            }

            byte[] body = Files.readAllBytes(output);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(contentTypeForFile(output.getFileName().toString()));
            headers.setContentDispositionFormData("attachment", output.getFileName().toString());
            headers.set(CHANNEL_HEADER, "rpa");
            return ResponseEntity.ok().headers(headers).body(body);
        } catch (Exception e) {
            log.error("RPA-2: ошибка при заполнении шаблона {}: {}", request.templateName(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(("Office bot failed: " + e.getMessage()).getBytes());
        }
    }

    @GetMapping("/stub-info")
    public ResponseEntity<Map<String, Object>> getStubInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("service", "Document Service");
        info.put("status", "active");
        info.put("version", "0.4.0-SNAPSHOT");
        info.put("mode", "stateless-bytes");
        info.put("documentTypes", new String[] {
                "receipt-order", "inventory-report",
                "revaluation-act", "write-off-act", "waybill", "picking-list",
                "receipt-act", "invoice", "transport-note", "cmr"
        });
        info.put("generationModes", new String[] {"auto (POI/PDFBox)", "rpa (WinAppDriver)"});
        info.put("rpaTemplatesBound", new String[] {
                "receipt-order", "revaluation-act", "inventory-report",
                "write-off-act", "waybill", "receipt-act (with discrepancies only)",
                "invoice", "transport-note", "cmr"
        });
        info.put("formats", new String[] {"pdf (default)", "rpa-xls", "rpa-docx"});
        info.put("hint", "POST <type> возвращает bytes напрямую. Mode через header X-Generation-Mode. "
                + "Хранение — product-service /api/document-registry");
        return ResponseEntity.ok(info);
    }

    private ResponseEntity<byte[]> wrap(GenerationResult result) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentTypeFor(result.format()));
        headers.set(CHANNEL_HEADER, result.channel());
        return ResponseEntity.ok().headers(headers).body(result.body());
    }

    private Path resolveTemplate(String name) {
        Path direct = Paths.get(rpaProperties.getTemplates().getDir() + name).toAbsolutePath();
        if (Files.exists(direct)) return direct;
        return Paths.get("documents template/" + name).toAbsolutePath();
    }

    private MediaType contentTypeForFile(String filename) throws IOException {
        String probed = Files.probeContentType(Path.of(filename));
        return probed != null ? MediaType.parseMediaType(probed) : MediaType.APPLICATION_OCTET_STREAM;
    }

    private MediaType contentTypeFor(String format) {
        return switch (format) {
            case "rpa-xls", "xls" -> MediaType.parseMediaType("application/vnd.ms-excel");
            case "xlsx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            case "rpa-docx", "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "doc" -> MediaType.parseMediaType("application/msword");
            case "rtf" -> MediaType.parseMediaType("application/rtf");
            default -> MediaType.APPLICATION_PDF;
        };
    }
}
