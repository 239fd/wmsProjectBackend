package by.bsuir.productservice.controller;

import by.bsuir.productservice.config.SecurityUtils;
import by.bsuir.productservice.dto.import_.SupplyDto;
import by.bsuir.productservice.rpa.ErpExtractorJob;
import by.bsuir.productservice.service.SupplyImportService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/supplies")
@RequiredArgsConstructor
@Tag(name = "Импорт поставок", description = "Импорт плановых поставок из 1С (RPA) и JSON")
public class SupplyImportController {

    private static final String SOURCE_1C = "1C-Python";
    private static final String SOURCE_JSON = "JSON";

    private final ErpExtractorJob extractorJob;
    private final SupplyImportService importService;

    @Qualifier("supplyImportMapper")
    private final ObjectMapper supplyImportMapper;

    @Operation(summary = "Импорт плановых поставок из 1С через RPA")
    @PostMapping("/import-1c")
    public ResponseEntity<Map<String, Object>> importFrom1c(
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-Warehouse-Id", required = false) UUID warehouseId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        String role = SecurityUtils.resolveRole(userRole);
        if (!"WORKER".equals(role) && !"DIRECTOR".equals(role)) {
            return ResponseEntity.status(403).build();
        }
        if (organizationId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "organizationId обязателен"));
        }
        return ResponseEntity.ok(extractorJob.runManually(organizationId, warehouseId, userId));
    }

    @Operation(summary = "Импорт плановых поставок из JSON-файла")
    @PostMapping(value = "/import-json", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importFromJson(
            @RequestParam("file") MultipartFile file,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestHeader(value = "X-Warehouse-Id", required = false) UUID warehouseId,
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestHeader(value = "X-User-Role", required = false) String userRole) {

        String role = SecurityUtils.resolveRole(userRole);
        if (!"WORKER".equals(role) && !"DIRECTOR".equals(role)) {
            return ResponseEntity.status(403).build();
        }
        if (organizationId == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "organizationId обязателен"));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Файл не передан или пустой"));
        }

        List<SupplyDto> supplies;
        try {
            supplies = parseSupplies(file.getBytes());
        } catch (IOException ex) {
            log.error("Не удалось прочитать файл: {}", ex.getMessage(), ex);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Не удалось прочитать файл: " + ex.getMessage()));
        } catch (IllegalArgumentException ex) {
            log.warn("JSON не соответствует схеме: {}", ex.getMessage());
            return ResponseEntity.unprocessableEntity()
                    .body(Map.of("error", ex.getMessage()));
        }

        Map<String, Object> validationErrors = validate(supplies);
        if (!validationErrors.isEmpty()) {
            Map<String, Object> body = new HashMap<>();
            body.put("error", "JSON не прошёл валидацию");
            body.put("details", validationErrors);
            return ResponseEntity.unprocessableEntity().body(body);
        }

        SupplyImportService.ImportResult result = importService.importSupplies(
                organizationId, warehouseId, userId, SOURCE_JSON, supplies);
        Map<String, Object> body = new HashMap<>(result.toMap());
        body.put("source", SOURCE_JSON);
        body.put("found", supplies.size());
        body.put("success", result.errored() == 0);
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "Скачать пример JSON для импорта")
    @GetMapping(value = "/sample-json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> sampleJson() {
        Resource resource = new ClassPathResource("sample-supply.json");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sample-supply.json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource);
    }

    private List<SupplyDto> parseSupplies(byte[] bytes) throws IOException {
        JsonNode root = supplyImportMapper.readTree(bytes);
        if (root == null || root.isNull()) {
            throw new IllegalArgumentException("Файл пустой или невалидный JSON");
        }
        JsonNode container = root.has("supplies") ? root.get("supplies") : root;
        List<SupplyDto> result = new ArrayList<>();
        if (container.isArray()) {
            for (JsonNode el : container) {
                JsonNode unwrapped = el.has("supply") ? el.get("supply") : el;
                result.add(supplyImportMapper.treeToValue(unwrapped, SupplyDto.class));
            }
        } else if (container.isObject()) {
            JsonNode unwrapped = container.has("supply") ? container.get("supply") : container;
            result.add(supplyImportMapper.treeToValue(unwrapped, SupplyDto.class));
        } else {
            throw new IllegalArgumentException("Ожидался объект или массив поставок");
        }
        return result;
    }

    private Map<String, Object> validate(List<SupplyDto> supplies) {
        Map<String, Object> errors = new HashMap<>();
        for (int i = 0; i < supplies.size(); i++) {
            SupplyDto s = supplies.get(i);
            List<String> rowErrors = new ArrayList<>();
            if (s.externalId() == null || s.externalId().isBlank()) {
                rowErrors.add("externalId обязателен");
            }
            if (!Boolean.TRUE.equals(s.quantityOnly())) {
                if (s.items() == null || s.items().isEmpty()) {
                    rowErrors.add("items обязателен (или укажите quantity_only=true)");
                } else {
                    for (int j = 0; j < s.items().size(); j++) {
                        SupplyDto.SupplyItemDto item = s.items().get(j);
                        if (item.expectedQty() == null) {
                            rowErrors.add("items[" + j + "].expectedQty обязателен");
                        }
                        if (item.product() == null
                                || item.product().sku() == null
                                || item.product().sku().isBlank()) {
                            rowErrors.add("items[" + j + "].product.sku обязателен");
                        }
                    }
                }
            } else {
                if (s.totalItems() == null || s.totalItems() <= 0) {
                    rowErrors.add("totalItems обязателен при quantity_only=true");
                }
            }
            if (!rowErrors.isEmpty()) {
                errors.put("supplies[" + i + "]", rowErrors);
            }
        }
        return errors;
    }
}
