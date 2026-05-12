package by.bsuir.productservice.service;

import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAnalyticsService {

    private final InventoryRepository inventoryRepository;
    private final ProductOperationRepository operationRepository;

    @Cacheable(value = "inventoryAnalytics")
    public Map<String, Object> getInventoryAnalytics() {
        log.info("Calculating inventory analytics");

        List<Inventory> allInventory = inventoryRepository.findAll();

        int totalQuantity = allInventory.stream()
                .mapToInt(inv -> inv.getQuantity().intValue())
                .sum();

        int reservedQuantity = allInventory.stream()
                .mapToInt(inv -> inv.getReservedQuantity().intValue())
                .sum();

        int availableQuantity = totalQuantity - reservedQuantity;

        long uniqueProducts = allInventory.stream()
                .map(Inventory::getProductId)
                .distinct()
                .count();

        Map<String, Object> analytics = new HashMap<>();
        analytics.put("totalQuantity", totalQuantity);
        analytics.put("reservedQuantity", reservedQuantity);
        analytics.put("availableQuantity", availableQuantity);
        analytics.put("uniqueProducts", uniqueProducts);
        analytics.put("totalRecords", allInventory.size());

        return analytics;
    }

    @Cacheable(value = "operationsDynamics", key = "#startDate + '-' + #endDate")
    public Map<String, Object> getOperationsDynamics(LocalDate startDate, LocalDate endDate) {
        log.info("Calculating operations dynamics from {} to {}", startDate, endDate);

        LocalDateTime startDateTime = startDate.atStartOfDay();
        LocalDateTime endDateTime = endDate.atTime(23, 59, 59);

        List<ProductOperation> operations = operationRepository.findAll().stream()
                .filter(op -> op.getOperationDate() != null)
                .filter(op -> !op.getOperationDate().isBefore(startDateTime))
                .filter(op -> !op.getOperationDate().isAfter(endDateTime))
                .collect(Collectors.toList());

        Map<String, Long> operationsByType = operations.stream()
                .collect(Collectors.groupingBy(
                        op -> op.getOperationType().name(),
                        Collectors.counting()
                ));

        Map<LocalDate, Long> dailyOperations = operations.stream()
                .collect(Collectors.groupingBy(
                        op -> op.getOperationDate().toLocalDate(),
                        Collectors.counting()
                ));

        Map<String, Long> operationsByUser = operations.stream()
                .filter(op -> op.getUserId() != null)
                .collect(Collectors.groupingBy(
                        op -> op.getUserId().toString(),
                        Collectors.counting()
                ));

        Map<String, Object> dynamics = new HashMap<>();
        dynamics.put("startDate", startDate);
        dynamics.put("endDate", endDate);
        dynamics.put("totalOperations", operations.size());
        dynamics.put("operationsByType", operationsByType);
        dynamics.put("dailyOperations", dailyOperations);
        dynamics.put("operationsByUser", operationsByUser);

        return dynamics;
    }


    public Map<String, Object> getInventoryComparison(LocalDate startDate, LocalDate endDate) {
        log.info("Calculating inventory comparison for [{} - {}]", startDate, endDate);

        Map<String, Object> current = getInventoryAnalytics();
        long totalNow = ((Number) current.get("totalQuantity")).longValue();
        long reservedNow = ((Number) current.get("reservedQuantity")).longValue();
        long availableNow = totalNow - reservedNow;

        LocalDateTime curStart = startDate.atStartOfDay();
        LocalDateTime curEnd = endDate.atTime(23, 59, 59);

        List<ProductOperation> opsInPeriod = operationRepository.findAll().stream()
                .filter(op -> op.getOperationDate() != null)
                .filter(op -> !op.getOperationDate().isBefore(curStart))
                .filter(op -> !op.getOperationDate().isAfter(curEnd))
                .collect(Collectors.toList());

        long inflow = opsInPeriod.stream()
                .filter(op -> "RECEIPT".equals(op.getOperationType().name())
                        || "RECEIVE".equals(op.getOperationType().name()))
                .mapToLong(op -> op.getQuantity() != null ? op.getQuantity().longValue() : 0L)
                .sum();
        long outflow = opsInPeriod.stream()
                .filter(op -> "SHIP".equals(op.getOperationType().name())
                        || "WRITE_OFF".equals(op.getOperationType().name())
                        || "WRITEOFF".equals(op.getOperationType().name()))
                .mapToLong(op -> op.getQuantity() != null ? op.getQuantity().longValue() : 0L)
                .sum();
        long delta = inflow - outflow;
        long totalAtStart = totalNow - delta;
        long availableAtStart = availableNow - delta;

        Double totalTrendPercent = null;
        Double availableTrendPercent = null;
        if (totalAtStart > 0) {
            totalTrendPercent = ((double) delta / totalAtStart) * 100.0;
        }
        if (availableAtStart > 0) {
            availableTrendPercent = ((double) delta / availableAtStart) * 100.0;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("currentStart", startDate);
        result.put("currentEnd", endDate);
        result.put("totalQuantityNow", totalNow);
        result.put("totalQuantityAtStart", totalAtStart);
        result.put("totalQuantityTrendPercent", totalTrendPercent);
        result.put("availableQuantityNow", availableNow);
        result.put("availableQuantityAtStart", availableAtStart);
        result.put("availableQuantityTrendPercent", availableTrendPercent);
        result.put("inflow", inflow);
        result.put("outflow", outflow);
        result.put("delta", delta);
        return result;
    }

    @Cacheable(value = "operationsComparison", key = "#startDate + '-' + #endDate")
    public Map<String, Object> getOperationsComparison(LocalDate startDate, LocalDate endDate) {
        long lengthDays = endDate.toEpochDay() - startDate.toEpochDay() + 1;
        LocalDate prevEnd = startDate.minusDays(1);
        LocalDate prevStart = prevEnd.minusDays(lengthDays - 1);

        log.info("Comparing operations: current [{} - {}] vs previous [{} - {}]",
                startDate, endDate, prevStart, prevEnd);

        LocalDateTime curStart = startDate.atStartOfDay();
        LocalDateTime curEnd = endDate.atTime(23, 59, 59);
        LocalDateTime preStart = prevStart.atStartOfDay();
        LocalDateTime preEnd = prevEnd.atTime(23, 59, 59);

        List<ProductOperation> all = operationRepository.findAll();

        long currentTotal = all.stream()
                .filter(op -> op.getOperationDate() != null)
                .filter(op -> !op.getOperationDate().isBefore(curStart))
                .filter(op -> !op.getOperationDate().isAfter(curEnd))
                .count();

        long previousTotal = all.stream()
                .filter(op -> op.getOperationDate() != null)
                .filter(op -> !op.getOperationDate().isBefore(preStart))
                .filter(op -> !op.getOperationDate().isAfter(preEnd))
                .count();


        Double deltaPercent = null;
        if (previousTotal > 0) {
            deltaPercent = ((double) (currentTotal - previousTotal) / previousTotal) * 100.0;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("currentStart", startDate);
        result.put("currentEnd", endDate);
        result.put("previousStart", prevStart);
        result.put("previousEnd", prevEnd);
        result.put("currentTotal", currentTotal);
        result.put("previousTotal", previousTotal);
        result.put("deltaPercent", deltaPercent);
        return result;
    }
}

