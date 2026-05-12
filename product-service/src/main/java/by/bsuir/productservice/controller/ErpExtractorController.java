package by.bsuir.productservice.controller;

import by.bsuir.productservice.model.entity.PlannedDelivery;
import by.bsuir.productservice.repository.ExtractionLogRepository;
import by.bsuir.productservice.repository.PlannedDeliveryRepository;
import by.bsuir.productservice.rpa.ErpExtractorJob;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/erp-extractor")
@RequiredArgsConstructor
@Tag(name = "RPA-экстрактор", description = "Управление извлечением плановых поставок из ERP (FR-5.1, BR-2)")
public class ErpExtractorController {

    private final ErpExtractorJob extractorJob;
    private final PlannedDeliveryRepository deliveryRepository;
    private final ExtractionLogRepository logRepository;

    @Operation(summary = "Запустить извлечение вручную",
               description = "Параметр mode: 'rpa' (HTML-скрейпинг) или 'api' (REST API). По умолчанию — режим из настроек.")
    @ApiResponse(responseCode = "200", description = "Извлечение выполнено")
    @PostMapping("/run")
    public ResponseEntity<Map<String, Object>> runExtraction(
            @RequestParam(required = false) String mode) {
        return ResponseEntity.ok(extractorJob.runManually(mode));
    }

    @Operation(summary = "Получить необработанные плановые поставки",
               description = "Возвращает поставки, загруженные из ERP, но ещё не связанные с реальными поставками в системе")
    @GetMapping("/deliveries")
    public ResponseEntity<List<PlannedDelivery>> getPendingDeliveries() {
        return ResponseEntity.ok(deliveryRepository.findByProcessedFalseOrderByExpectedDateAsc());
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