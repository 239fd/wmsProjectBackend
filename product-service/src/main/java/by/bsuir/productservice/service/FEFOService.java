package by.bsuir.productservice.service;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.model.enums.AllocationStrategy;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class FEFOService {

    private final InventoryRepository inventoryRepository;
    private final ProductBatchRepository batchRepository;

    public List<InventoryAllocation> selectInventoryByFEFO(
            UUID productId,
            UUID warehouseId,
            BigDecimal requiredQuantity) {
        return selectInventory(productId, warehouseId, requiredQuantity, AllocationStrategy.AUTO);
    }

    public List<InventoryAllocation> selectInventory(
            UUID productId,
            UUID warehouseId,
            BigDecimal requiredQuantity,
            AllocationStrategy strategy) {

        log.info("Selecting inventory ({}) for product {} at warehouse {}, qty {}",
                strategy, productId, warehouseId, requiredQuantity);

        List<Inventory> availableInventory = inventoryRepository.findByWarehouseId(warehouseId).stream()
                .filter(inv -> productId.equals(inv.getProductId()))
                .filter(inv -> inv.getStatus() == null
                        || inv.getStatus() == by.bsuir.productservice.model.enums.InventoryStatus.AVAILABLE)
                .filter(inv -> inv.getQuantity() != null
                        && inv.getQuantity().subtract(
                                inv.getReservedQuantity() != null ? inv.getReservedQuantity() : BigDecimal.ZERO)
                                .compareTo(BigDecimal.ZERO) > 0)
                .toList();

        if (availableInventory.isEmpty()) {
            throw AppException.notFound("Товар отсутствует на складе или весь остаток зарезервирован");
        }

        List<InventoryWithBatch> pool = enrichWithBatchInfo(availableInventory);

        AllocationStrategy effective = strategy;
        if (strategy == null || strategy == AllocationStrategy.AUTO) {
            boolean anyExpiry = pool.stream()
                    .anyMatch(iwb -> iwb.batch != null && iwb.batch.getExpiryDate() != null);
            effective = anyExpiry ? AllocationStrategy.FEFO : AllocationStrategy.FIFO;
            log.info("AUTO стратегия → {} (anyExpiry={})", effective, anyExpiry);
        }

        Comparator<InventoryWithBatch> sorter = switch (effective) {
            case FEFO -> Comparator
                    .<InventoryWithBatch, LocalDate>comparing(
                            iwb -> iwb.batch != null && iwb.batch.getExpiryDate() != null
                                    ? iwb.batch.getExpiryDate() : LocalDate.MAX)
                    .thenComparing(
                            iwb -> iwb.batch != null && iwb.batch.getCreatedAt() != null
                                    ? iwb.batch.getCreatedAt() : LocalDateTime.MAX);
            case FIFO -> Comparator
                    .<InventoryWithBatch, LocalDateTime>comparing(
                            iwb -> iwb.batch != null && iwb.batch.getCreatedAt() != null
                                    ? iwb.batch.getCreatedAt() : LocalDateTime.MAX)
                    .thenComparing(
                            iwb -> iwb.inventory.getLastUpdated() != null
                                    ? iwb.inventory.getLastUpdated() : LocalDateTime.MAX);
            default -> Comparator.comparing(
                    iwb -> iwb.batch != null && iwb.batch.getExpiryDate() != null
                            ? iwb.batch.getExpiryDate() : LocalDate.MAX);
        };
        pool = new ArrayList<>(pool);
        pool.sort(sorter);

        LocalDate today = LocalDate.now();
        List<InventoryWithBatch> expired = new ArrayList<>();
        List<InventoryWithBatch> usable = new ArrayList<>();
        for (InventoryWithBatch iwb : pool) {
            if (iwb.batch != null && iwb.batch.getExpiryDate() != null
                    && iwb.batch.getExpiryDate().isBefore(today)) {
                expired.add(iwb);
            } else {
                usable.add(iwb);
            }
        }
        if (!expired.isEmpty()) {
            log.warn("Стратегия {}: пропущено {} просроченных партий: {}",
                    effective, expired.size(),
                    expired.stream().map(iwb -> iwb.batch.getBatchId() + "(до " + iwb.batch.getExpiryDate() + ")").toList());
        }

        List<InventoryAllocation> allocations = new ArrayList<>();
        BigDecimal remaining = requiredQuantity;

        for (InventoryWithBatch iwb : usable) {
            Inventory inv = iwb.inventory;
            BigDecimal reserved = inv.getReservedQuantity() != null ? inv.getReservedQuantity() : BigDecimal.ZERO;
            BigDecimal available = inv.getQuantity().subtract(reserved);
            if (available.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal toAllocate = remaining.min(available);
            allocations.add(new InventoryAllocation(
                    inv.getInventoryId(), inv.getProductId(), inv.getBatchId(),
                    inv.getWarehouseId(), inv.getCellId(),
                    toAllocate,
                    iwb.batch != null ? iwb.batch.getExpiryDate() : null));
            log.info("[{}] +{} из inv={} (batch={}, cell={}, expiry={}, createdAt={})",
                    effective, toAllocate, inv.getInventoryId(),
                    iwb.batch != null ? iwb.batch.getBatchNumber() : null,
                    inv.getCellId(),
                    iwb.batch != null ? iwb.batch.getExpiryDate() : null,
                    iwb.batch != null ? iwb.batch.getCreatedAt() : null);

            remaining = remaining.subtract(toAllocate);
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) break;
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal usableTotal = usable.stream()
                    .map(iwb -> iwb.inventory.getQuantity().subtract(
                            iwb.inventory.getReservedQuantity() != null ? iwb.inventory.getReservedQuantity() : BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            String hint = expired.isEmpty() ? "" :
                    " (исключено " + expired.size() + " просроченных партий)";
            throw AppException.badRequest(String.format(
                    "Недостаточно товара на складе. Доступно: %s, требуется: %s%s",
                    usableTotal, requiredQuantity, hint));
        }

        log.info("Стратегия {} выбрала {} позиций", effective, allocations.size());
        return allocations;
    }

    private List<InventoryWithBatch> enrichWithBatchInfo(List<Inventory> inventories) {
        List<InventoryWithBatch> result = new ArrayList<>();

        for (Inventory inv : inventories) {
            ProductBatch batch = null;
            if (inv.getBatchId() != null) {
                batch = batchRepository.findById(inv.getBatchId()).orElse(null);
            }
            result.add(new InventoryWithBatch(inv, batch));
        }

        return result;
    }

    private record InventoryWithBatch(Inventory inventory, ProductBatch batch) {
    }

    @Getter
    public static class InventoryAllocation {
        private final UUID inventoryId;
        private final UUID productId;
        private final UUID batchId;
        private final UUID warehouseId;
        private final UUID cellId;
        private final BigDecimal quantity;
        private final LocalDate expiryDate;

        public InventoryAllocation(UUID inventoryId, UUID productId, UUID batchId,
                                  UUID warehouseId, UUID cellId,
                                  BigDecimal quantity, LocalDate expiryDate) {
            this.inventoryId = inventoryId;
            this.productId = productId;
            this.batchId = batchId;
            this.warehouseId = warehouseId;
            this.cellId = cellId;
            this.quantity = quantity;
            this.expiryDate = expiryDate;
        }

        @Override
        public String toString() {
            return String.format("Allocation[inventory=%s, batch=%s, quantity=%s, expiry=%s]",
                    inventoryId, batchId, quantity, expiryDate);
        }
    }
}
