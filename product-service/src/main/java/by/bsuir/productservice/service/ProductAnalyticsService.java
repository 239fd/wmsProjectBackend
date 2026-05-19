package by.bsuir.productservice.service;

import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.entity.ProductOperation;
import by.bsuir.productservice.model.entity.ProductReadModel;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import by.bsuir.productservice.repository.ProductOperationRepository;
import by.bsuir.productservice.repository.ProductReadModelRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductAnalyticsService {

    private final InventoryRepository inventoryRepository;
    private final ProductOperationRepository operationRepository;
    private final ProductBatchRepository batchRepository;
    private final ProductReadModelRepository productReadModelRepository;

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

        // reserved тренд: считаем изменение reservedQuantity по операциям RESERVE/RELEASE
        long reserveDelta = opsInPeriod.stream()
                .filter(op -> "RESERVE".equals(op.getOperationType().name()))
                .mapToLong(op -> op.getQuantity() != null ? op.getQuantity().longValue() : 0L)
                .sum()
                - opsInPeriod.stream()
                .filter(op -> "RELEASE".equals(op.getOperationType().name())
                        || "SHIP".equals(op.getOperationType().name()))
                .mapToLong(op -> op.getQuantity() != null ? op.getQuantity().longValue() : 0L)
                .sum();
        long reservedAtStart = reservedNow - reserveDelta;

        long uniqueNow = ((Number) current.get("uniqueProducts")).longValue();
        long newProducts = opsInPeriod.stream()
                .filter(op -> "RECEIPT".equals(op.getOperationType().name())
                        || "RECEIVE".equals(op.getOperationType().name()))
                .map(ProductOperation::getProductId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        long uniqueAtStart = Math.max(0L, uniqueNow - newProducts);

        Double totalTrendPercent = trendPercent(delta, totalAtStart);
        Double availableTrendPercent = trendPercent(delta, availableAtStart);
        Double reservedTrendPercent = trendPercent(reserveDelta, reservedAtStart);
        Double uniqueTrendPercent = trendPercent(newProducts, uniqueAtStart);

        Map<String, Object> result = new HashMap<>();
        result.put("currentStart", startDate);
        result.put("currentEnd", endDate);
        result.put("totalQuantityNow", totalNow);
        result.put("totalQuantityAtStart", totalAtStart);
        result.put("totalQuantityTrendPercent", totalTrendPercent);
        result.put("availableQuantityNow", availableNow);
        result.put("availableQuantityAtStart", availableAtStart);
        result.put("availableQuantityTrendPercent", availableTrendPercent);
        result.put("reservedQuantityNow", reservedNow);
        result.put("reservedQuantityAtStart", reservedAtStart);
        result.put("reservedQuantityTrendPercent", reservedTrendPercent);
        result.put("uniqueProductsNow", uniqueNow);
        result.put("uniqueProductsAtStart", uniqueAtStart);
        result.put("uniqueProductsTrendPercent", uniqueTrendPercent);
        result.put("inflow", inflow);
        result.put("outflow", outflow);
        result.put("delta", delta);
        return result;
    }

    private Double trendPercent(long delta, long base) {
        if (base <= 0) {
            return null;
        }
        return ((double) delta / base) * 100.0;
    }

    /**
     * ABC-распределение товаров. Для каждой категории A/B/C возвращает количество позиций
     * и суммарный остаток на всех складах.
     */
    @Cacheable(value = "abcDistribution")
    public Map<String, Object> getAbcDistribution() {
        log.info("Calculating ABC distribution");

        List<ProductReadModel> products = productReadModelRepository.findAll();
        List<Inventory> inventories = inventoryRepository.findAll();

        Map<UUID, BigDecimal> qtyByProduct = inventories.stream()
                .collect(Collectors.toMap(
                        Inventory::getProductId,
                        inv -> inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO,
                        BigDecimal::add));

        Map<String, Long> productCountByClass = new LinkedHashMap<>();
        Map<String, BigDecimal> qtyByClass = new LinkedHashMap<>();
        for (String cls : List.of("A", "B", "C")) {
            productCountByClass.put(cls, 0L);
            qtyByClass.put(cls, BigDecimal.ZERO);
        }

        for (ProductReadModel p : products) {
            String cls = p.getAbcClass() != null ? p.getAbcClass() : "C";
            if (!productCountByClass.containsKey(cls)) {
                continue;
            }
            productCountByClass.merge(cls, 1L, Long::sum);
            BigDecimal q = qtyByProduct.getOrDefault(p.getProductId(), BigDecimal.ZERO);
            qtyByClass.merge(cls, q, BigDecimal::add);
        }

        long totalProducts = productCountByClass.values().stream().mapToLong(Long::longValue).sum();
        BigDecimal totalQty = qtyByClass.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, Object> result = new HashMap<>();
        result.put("productCountByClass", productCountByClass);
        result.put("quantityByClass", qtyByClass);
        result.put("totalProducts", totalProducts);
        result.put("totalQuantity", totalQty);
        return result;
    }

    /**
     * Товары с истекающим сроком годности в течение withinDays дней.
     * Возвращает плоский список с productId/batchId/expiryDate/daysLeft/quantity.
     */
    public List<Map<String, Object>> getExpiringProducts(int withinDays) {
        log.info("Listing products expiring within {} days", withinDays);

        LocalDate today = LocalDate.now();
        LocalDate cutoff = today.plusDays(withinDays);

        List<ProductBatch> batches = batchRepository.findAll().stream()
                .filter(b -> b.getExpiryDate() != null)
                .filter(b -> !b.getExpiryDate().isAfter(cutoff))
                .collect(Collectors.toList());

        if (batches.isEmpty()) {
            return List.of();
        }

        Set<UUID> batchIds = batches.stream().map(ProductBatch::getBatchId).collect(Collectors.toSet());
        Map<UUID, BigDecimal> qtyByBatch = inventoryRepository.findAll().stream()
                .filter(inv -> inv.getBatchId() != null && batchIds.contains(inv.getBatchId()))
                .collect(Collectors.toMap(
                        Inventory::getBatchId,
                        inv -> inv.getQuantity() != null ? inv.getQuantity() : BigDecimal.ZERO,
                        BigDecimal::add));

        Set<UUID> productIds = batches.stream().map(ProductBatch::getProductId).collect(Collectors.toSet());
        Map<UUID, ProductReadModel> productById = productReadModelRepository.findAllById(productIds).stream()
                .collect(Collectors.toMap(ProductReadModel::getProductId, p -> p));

        return batches.stream()
                .map(b -> {
                    long daysLeft = ChronoUnit.DAYS.between(today, b.getExpiryDate());
                    ProductReadModel p = productById.get(b.getProductId());
                    Map<String, Object> row = new HashMap<>();
                    row.put("batchId", b.getBatchId());
                    row.put("batchNumber", b.getBatchNumber());
                    row.put("productId", b.getProductId());
                    row.put("productName", p != null ? p.getName() : "—");
                    row.put("sku", p != null ? p.getSku() : "—");
                    row.put("expiryDate", b.getExpiryDate());
                    row.put("daysLeft", daysLeft);
                    row.put("quantity", qtyByBatch.getOrDefault(b.getBatchId(), BigDecimal.ZERO));
                    row.put("storageConditions", b.getStorageConditions() != null ? b.getStorageConditions().name() : null);
                    row.put("urgency", daysLeft < 0 ? "EXPIRED" : daysLeft <= 7 ? "CRITICAL" : daysLeft <= 14 ? "WARNING" : "INFO");
                    return row;
                })
                .sorted(Comparator.comparing(m -> (LocalDate) m.get("expiryDate")))
                .collect(Collectors.toList());
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

