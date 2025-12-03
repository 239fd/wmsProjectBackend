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
}

