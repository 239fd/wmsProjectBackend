package by.bsuir.productservice.service;

import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.model.enums.OperationType;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AbcAnalysisService {

    private static final int LOOKBACK_DAYS = 90;
    private static final double CLASS_A_THRESHOLD = 0.80;
    private static final double CLASS_B_THRESHOLD = 0.95;

    private final ProductOperationRepository operationRepository;
    private final ProductReadModelRepository productRepository;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void runDailyAbcAnalysis() {
        log.info("Запуск ежедневного ABC-анализа товаров");
        calculateAndSave();
        log.info("ABC-анализ завершён");
    }

    @Transactional
    public Map<String, Object> runManually() {
        log.info("Ручной запуск ABC-анализа");
        Map<String, Long> classCounts = calculateAndSave();
        Map<String, Object> result = new HashMap<>();
        result.put("message", "ABC-анализ выполнен");
        result.put("period_days", LOOKBACK_DAYS);
        result.put("class_a_count", classCounts.getOrDefault("A", 0L));
        result.put("class_b_count", classCounts.getOrDefault("B", 0L));
        result.put("class_c_count", classCounts.getOrDefault("C", 0L));
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAbcReport() {
        return productRepository.findAll().stream()
                .filter(p -> p.getAbcClass() != null)
                .sorted(Comparator.comparing(p -> p.getAbcClass() == null ? "Z" : p.getAbcClass()))
                .map(p -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("productId", p.getProductId());
                    m.put("name", p.getName());
                    m.put("sku", p.getSku());
                    m.put("abcClass", p.getAbcClass());
                    return m;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Long> calculateAndSave() {
        LocalDateTime from = LocalDate.now().minusDays(LOOKBACK_DAYS).atStartOfDay();
        LocalDateTime to = LocalDateTime.now();

        List<ProductOperation> operations = operationRepository
                .findByOperationDateBetween(from, to);

        Map<UUID, BigDecimal> turnoverByProduct = new HashMap<>();
        for (ProductOperation op : operations) {
            if (op.getOperationType() == OperationType.SHIPMENT
                    || op.getOperationType() == OperationType.WRITE_OFF) {
                turnoverByProduct.merge(op.getProductId(), op.getQuantity(), BigDecimal::add);
            }
        }

        if (turnoverByProduct.isEmpty()) {
            log.info("Нет операций за последние {} дней, ABC-классы не пересчитываются", LOOKBACK_DAYS);
            return Map.of();
        }

        BigDecimal totalTurnover = turnoverByProduct.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map.Entry<UUID, BigDecimal>> sorted = new ArrayList<>(turnoverByProduct.entrySet());
        sorted.sort(Map.Entry.<UUID, BigDecimal>comparingByValue().reversed());

        Map<UUID, String> abcClasses = new HashMap<>();
        BigDecimal cumulative = BigDecimal.ZERO;

        for (Map.Entry<UUID, BigDecimal> entry : sorted) {
            cumulative = cumulative.add(entry.getValue());
            double share = cumulative.doubleValue() / totalTurnover.doubleValue();
            String cls = share <= CLASS_A_THRESHOLD ? "A" : share <= CLASS_B_THRESHOLD ? "B" : "C";
            abcClasses.put(entry.getKey(), cls);
        }

        Map<String, Long> classCounts = new HashMap<>();
        for (Map.Entry<UUID, String> e : abcClasses.entrySet()) {
            productRepository.findById(e.getKey()).ifPresent(product -> {
                product.setAbcClass(e.getValue());
                productRepository.save(product);
            });
            classCounts.merge(e.getValue(), 1L, Long::sum);
        }

        log.info("ABC-анализ: A={}, B={}, C={}",
                classCounts.getOrDefault("A", 0L),
                classCounts.getOrDefault("B", 0L),
                classCounts.getOrDefault("C", 0L));
        return classCounts;
    }
}