package by.bsuir.productservice.controller;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.GeneratedDocument;
import by.bsuir.productservice.repository.GeneratedDocumentRepository;
import by.bsuir.productservice.service.DocumentRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/document-registry")
@RequiredArgsConstructor
@Tag(name = "Реестр документов",
        description = "Список и скачивание сгенерированных документов организации")
public class DocumentRegistryController {

    private static final int MAX_PAGE_SIZE = 100;

    private final GeneratedDocumentRepository repository;
    private final DocumentRegistryService registryService;

    private UUID resolveOrgId(UUID fromHeader) {
        UUID effective = fromHeader != null ? fromHeader
                : by.bsuir.productservice.config.tenant.TenantContext.get();
        if (effective == null) {
            effective = by.bsuir.productservice.config.SecurityUtils.resolveOrgId(null);
        }
        if (effective == null) {
            throw AppException.badRequest(
                    "Не удалось определить организацию. Зарегистрируйте организацию или войдите снова, чтобы обновить токен.");
        }
        return effective;
    }

    @Operation(summary = "Список документов организации (с пагинацией)")
    @GetMapping
    public ResponseEntity<Page<GeneratedDocument>> list(
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "generatedAt") Pageable pageable) {

        organizationId = resolveOrgId(organizationId);
        Pageable effective = pageable.getPageSize() > MAX_PAGE_SIZE
                ? org.springframework.data.domain.PageRequest.of(
                        pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort())
                : pageable;

        Page<GeneratedDocument> page = (type == null || type.isBlank())
                ? repository.findByOrganizationId(organizationId, effective)
                : repository.findByOrganizationIdAndDocumentType(organizationId, type, effective);

        return ResponseEntity.ok(page);
    }

    @Operation(summary = "Метаданные документа по id")
    @GetMapping("/{documentId}")
    public ResponseEntity<GeneratedDocument> getOne(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        organizationId = resolveOrgId(organizationId);
        GeneratedDocument document = repository
                .findByIdAndOrganizationId(documentId, organizationId)
                .orElseThrow(() -> AppException.notFound("Документ не найден: " + documentId));
        return ResponseEntity.ok(document);
    }

    @Operation(summary = "Скачать PDF документа (inline)")
    @GetMapping("/{documentId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        organizationId = resolveOrgId(organizationId);
        byte[] pdf = registryService.downloadBytes(documentId, organizationId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDispositionFormData("inline", documentId + ".pdf");
        return ResponseEntity.ok().headers(headers).body(pdf);
    }

    @Operation(summary = "Получить presigned URL для скачивания (TTL по настройке)")
    @GetMapping("/{documentId}/url")
    public ResponseEntity<Map<String, String>> presignedUrl(
            @PathVariable UUID documentId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        organizationId = resolveOrgId(organizationId);
        String url = registryService.presignedUrl(documentId, organizationId);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @Operation(summary = "Документы по операции (приёмка/отгрузка/...)")
    @GetMapping("/by-operation/{operationId}")
    public ResponseEntity<List<GeneratedDocument>> byOperation(
            @PathVariable UUID operationId,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {

        organizationId = resolveOrgId(organizationId);
        return ResponseEntity.ok(
                repository.findByOrganizationIdAndOperationIdOrderByGeneratedAtDesc(
                        organizationId, operationId));
    }
}
