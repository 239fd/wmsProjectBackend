package by.bsuir.productservice.rpa;

import by.bsuir.productservice.dto.import_.SupplyDto;
import by.bsuir.productservice.model.entity.ExtractionLog;
import by.bsuir.productservice.repository.ExtractionLogRepository;
import by.bsuir.productservice.service.SupplyImportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErpExtractorJob {

    private final ExtractionLogRepository logRepository;
    private final SupplyImportService importService;

    @Autowired(required = false)
    @Qualifier("oneCExtractor")
    private SupplyExtractor pythonExtractor;

    @Scheduled(cron = "0 0 3 * * *")
    public void runScheduled() {
        log.info("ErpExtractorJob: плановый запуск в {}", LocalDateTime.now());
        runManually(null, null, null);
    }

    @Transactional
    public Map<String, Object> runManually(UUID organizationId, UUID warehouseId, UUID userId) {
        if (pythonExtractor == null) {
            log.warn("Python RPA extractor выключен (rpa.python.enabled=false)");
            return Map.of("source", "1C-Python", "found", 0, "imported", 0,
                    "skipped", 0, "errored", 0, "success", false,
                    "error", "Python RPA не настроен");
        }
        String source = pythonExtractor.getSourceName();
        LocalDateTime startedAt = LocalDateTime.now();

        int found = 0;
        SupplyImportService.ImportResult importResult =
                new SupplyImportService.ImportResult(0, 0, 0, Collections.emptyList());
        String error = null;

        try {
            List<SupplyDto> supplies = pythonExtractor.extractSupplies();
            found = supplies.size();
            if (organizationId != null) {
                importResult = importService.importSupplies(
                        organizationId, warehouseId, userId, source, supplies);
            } else {
                log.warn("ErpExtractorJob: organizationId не задан — поставки не импортированы (extracted={})", found);
                error = "organizationId не задан";
            }
        } catch (Exception e) {
            log.error("ErpExtractorJob: ошибка: {}", e.getMessage(), e);
            error = e.getMessage();
        }

        ExtractionLog logRecord = ExtractionLog.builder()
                .source(source)
                .extractedAt(startedAt)
                .recordsFound(found)
                .recordsNew(importResult.imported())
                .success(error == null)
                .errorMessage(error)
                .build();
        logRepository.save(logRecord);

        log.info("ErpExtractorJob [{}]: found={}, imported={}, skipped={}, errored={}, error={}",
                source, found, importResult.imported(), importResult.skipped(),
                importResult.errored(), error);

        Map<String, Object> result = new HashMap<>();
        result.put("source", source);
        result.put("found", found);
        result.put("imported", importResult.imported());
        result.put("skipped", importResult.skipped());
        result.put("errored", importResult.errored());
        result.put("errors", importResult.errors());
        result.put("success", error == null);
        if (error != null) result.put("error", error);
        return result;
    }
}
