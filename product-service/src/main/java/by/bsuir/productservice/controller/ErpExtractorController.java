package by.bsuir.productservice.controller;

import by.bsuir.productservice.dto.request.RunErpExtractionRequest;
import by.bsuir.productservice.model.entity.ErpConnection;
import by.bsuir.productservice.model.entity.PlannedDelivery;
import by.bsuir.productservice.repository.ExtractionLogRepository;
import by.bsuir.productservice.repository.PlannedDeliveryRepository;
import by.bsuir.productservice.rpa.ErpConnectionParams;
import by.bsuir.productservice.rpa.ErpExtractorJob;
import by.bsuir.productservice.service.ErpConnectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/erp-extractor")
@RequiredArgsConstructor
@Tag(name = "RPA-экстрактор", description = "Управление извлечением плановых поставок из ERP (FR-5.1, BR-2)")
public class ErpExtractorController {

    private final ErpExtractorJob extractorJob;
    private final PlannedDeliveryRepository deliveryRepository;
    private final ExtractionLogRepository logRepository;
    private final ErpConnectionService erpConnectionService;

    private static final int MAX_PAGE_SIZE = 100;

    @Operation(summary = "Запустить извлечение вручную",
               description = "Параметр mode: 'onec' (1С толстый через WinAppDriver), 'rpa' (HTML-скрейпинг) или 'api' (REST API). "
                       + "Body может содержать connectionId (берёт креды из БД) или inline-credentials.")
    @ApiResponse(responseCode = "200", description = "Извлечение выполнено")
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runExtraction(
            @RequestParam(required = false) String mode,
            @RequestBody(required = false) RunErpExtractionRequest body,
            @RequestHeader(value = "X-Organization-Id", required = false) UUID organizationId) {
        ErpConnectionParams params = resolveParams(mode, body, organizationId);
        return ResponseEntity.ok(extractorJob.runManually(mode, params));
    }

    private ErpConnectionParams resolveParams(String mode, RunErpExtractionRequest body, UUID organizationId) {
        if (body != null && body.connectionId() != null) {
            Optional<ErpConnection> stored = erpConnectionService.findById(body.connectionId(), organizationId);
            if (stored.isPresent()) {
                ErpConnection c = stored.get();
                return new ErpConnectionParams(
                        c.getAggregator(), c.getUsername(), c.getPassword(),
                        c.getBasePath(), c.getSectionName(), c.getJournalName(), c.getDriverUrl());
            }
        }
        if (body != null) {
            ErpConnectionParams inline = new ErpConnectionParams(
                    body.aggregator() != null ? body.aggregator() : mode,
                    body.username(), body.password(),
                    body.basePath(), body.sectionName(), body.journalName(), body.driverUrl());
            if (!inline.isEmpty()) {
                return inline;
            }
        }
        if (organizationId != null) {
            Optional<ErpConnection> dflt = erpConnectionService.findDefault(organizationId, mode);
            if (dflt.isPresent()) {
                ErpConnection c = dflt.get();
                return new ErpConnectionParams(
                        c.getAggregator(), c.getUsername(), c.getPassword(),
                        c.getBasePath(), c.getSectionName(), c.getJournalName(), c.getDriverUrl());
            }
        }
        return null;
    }

    @Operation(summary = "Получить необработанные плановые поставки (пагинация)",
               description = "Возвращает страницу поставок, загруженных из ERP, но ещё не связанных с реальными")
    @GetMapping("/deliveries")
    public ResponseEntity<Page<PlannedDelivery>> getPendingDeliveries(
            @PageableDefault(size = 20, sort = "expectedDate", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(deliveryRepository.findByProcessedFalseOrderByExpectedDateAsc(capSize(pageable)));
    }

    private static Pageable capSize(Pageable pageable) {
        if (pageable.getPageSize() <= MAX_PAGE_SIZE) return pageable;
        return PageRequest.of(pageable.getPageNumber(), MAX_PAGE_SIZE, pageable.getSort());
    }

    @Operation(summary = "Получить журнал извлечений",
               description = "Последние 10 запусков экстрактора по каждому источнику (RPA и API)")
    @GetMapping("/log")
    public ResponseEntity<Map<String, Object>> getExtractionLog() {
        return ResponseEntity.ok(Map.of(
                "rpa", logRepository.findTop10BySourceOrderByExtractedAtDesc("RPA"),
                "api", logRepository.findTop10BySourceOrderByExtractedAtDesc("API")
        ));
    }
}
