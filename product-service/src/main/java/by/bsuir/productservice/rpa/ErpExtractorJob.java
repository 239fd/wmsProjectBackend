package by.bsuir.productservice.rpa;

import by.bsuir.productservice.config.RabbitMQConfig;
import by.bsuir.productservice.model.entity.ExtractionLog;
import by.bsuir.productservice.model.entity.PlannedDelivery;
import by.bsuir.productservice.repository.ExtractionLogRepository;
import by.bsuir.productservice.repository.PlannedDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ErpExtractorJob {

    private final PlannedDeliveryRepository deliveryRepository;
    private final ExtractionLogRepository logRepository;
    private final RabbitTemplate rabbitTemplate;

    @Qualifier("rpaExtractor")
    private final PlannedDeliveryExtractor rpaExtractor;

    @Qualifier("apiExtractor")
    private final PlannedDeliveryExtractor apiExtractor;

    @Autowired(required = false)
    @Qualifier("oneCExtractor")
    private PlannedDeliveryExtractor oneCExtractor;

    @Value("${erp.extraction.mode:onec}")
    private String extractionMode;

    @Scheduled(cron = "0 0 3 * * *")
    public void runScheduled() {
        log.info("ErpExtractorJob: плановый запуск в {}", LocalDateTime.now());
        run(extractionMode, null);
    }

    @Transactional
    public Map<String, Object> runManually(String mode) {
        return runManually(mode, null);
    }

    @Transactional
    public Map<String, Object> runManually(String mode, ErpConnectionParams params) {
        log.info("ErpExtractorJob: ручной запуск, режим={}, params={}", mode, params != null && !params.isEmpty());
        return run(mode != null ? mode : extractionMode, params);
    }

    private Map<String, Object> run(String mode, ErpConnectionParams params) {
        PlannedDeliveryExtractor extractor;
        if ("onec".equalsIgnoreCase(mode)) {
            if (oneCExtractor == null) {
                log.warn("1C extractor выключен (rpa.onec.enabled=false), fallback на rpaExtractor");
                extractor = rpaExtractor;
            } else {
                extractor = oneCExtractor;
            }
        } else if ("api".equalsIgnoreCase(mode)) {
            extractor = apiExtractor;
        } else {
            extractor = rpaExtractor;
        }
        String source = extractor.getSourceName();
        LocalDateTime startedAt = LocalDateTime.now();

        int found = 0;
        int newCount = 0;
        String error = null;

        try {
            List<Map<String, Object>> deliveries = (params != null && !params.isEmpty())
                    ? extractor.extractDeliveries(params)
                    : extractor.extractDeliveries();
            found = deliveries.size();

            for (Map<String, Object> d : deliveries) {
                String externalId = (String) d.get("externalId");
                if (externalId == null || deliveryRepository.existsByExternalId(externalId)) {
                    continue;
                }

                LocalDate expectedDate = null;
                Object dateObj = d.get("expectedDate");
                if (dateObj != null) {
                    try {
                        expectedDate = LocalDate.parse(dateObj.toString());
                    } catch (Exception ex) {
                        log.warn("Не удалось распарсить дату: {}", dateObj);
                    }
                }

                int qty = 0;
                Object qtyObj = d.get("expectedQuantity");
                if (qtyObj instanceof Integer) {
                    qty = (Integer) qtyObj;
                } else if (qtyObj != null) {
                    try { qty = Integer.parseInt(qtyObj.toString()); } catch (Exception ignored) {}
                }

                PlannedDelivery delivery = PlannedDelivery.builder()
                        .externalId(externalId)
                        .supplierName((String) d.getOrDefault("supplierName", "—"))
                        .productName((String) d.getOrDefault("productName", "—"))
                        .expectedQuantity(qty)
                        .expectedDate(expectedDate)
                        .source(source)
                        .build();
                deliveryRepository.save(delivery);
                newCount++;

                publishEvent(delivery, source);
            }

        } catch (Exception e) {
            log.error("ErpExtractorJob: ошибка: {}", e.getMessage(), e);
            error = e.getMessage();
        }

        ExtractionLog log2 = ExtractionLog.builder()
                .source(source)
                .extractedAt(startedAt)
                .recordsFound(found)
                .recordsNew(newCount)
                .success(error == null)
                .errorMessage(error)
                .build();
        logRepository.save(log2);

        log.info("ErpExtractorJob [{}]: найдено={}, новых={}, ошибка={}", source, found, newCount, error);

        Map<String, Object> result = new HashMap<>();
        result.put("source", source);
        result.put("found", found);
        result.put("new", newCount);
        result.put("success", error == null);
        if (error != null) result.put("error", error);
        return result;
    }

    private void publishEvent(PlannedDelivery delivery, String source) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "PLANNED_DELIVERY_RECEIVED");
            event.put("externalId", delivery.getExternalId());
            event.put("supplierName", delivery.getSupplierName());
            event.put("productName", delivery.getProductName());
            event.put("expectedQuantity", delivery.getExpectedQuantity());
            event.put("expectedDate", delivery.getExpectedDate() != null
                    ? delivery.getExpectedDate().toString() : null);
            event.put("source", source);
            event.put("timestamp", LocalDateTime.now().toString());

            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.PRODUCT_EXCHANGE,
                    "product.planned_delivery_received",
                    event
            );
        } catch (Exception e) {
            log.error("ErpExtractorJob: не удалось опубликовать событие: {}", e.getMessage());
        }
    }
}