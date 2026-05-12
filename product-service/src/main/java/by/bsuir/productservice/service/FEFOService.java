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
                .toList();

        if (availableInventory.isEmpty()) {
            throw AppException.notFound("Товар отсутствует на складе");
        }

        List<InventoryWithBatch> inventoryWithBatches = enrichWithBatchInfo(availableInventory);

        AllocationStrategy effective = strategy;
        if (strategy == AllocationStrategy.AUTO) {
            boolean anyExpiry = inventoryWithBatches.stream()
                    .anyMatch(iwb -> iwb.batch != null && iwb.batch.getExpiryDate() != null);
            effective = anyExpiry ? AllocationStrategy.FEFO : AllocationStrategy.FIFO;
            log.info("AUTO resolved to {} (anyExpiry={})", effective, anyExpiry);
        }

        Comparator<InventoryWithBatch> sorter = switch (effective) {
            case FEFO -> Comparator.comparing(iwb ->
                    iwb.batch != null && iwb.batch.getExpiryDate() != null
                            ? iwb.batch.getExpiryDate()
                            : LocalDate.MAX);
            case FIFO -> Comparator.comparing((InventoryWithBatch iwb) ->
                    iwb.batch != null && iwb.batch.getCreatedAt() != null
                            ? iwb.batch.getCreatedAt()
                            : LocalDateTime.MAX);
            default -> Comparator.comparing(iwb ->
                    iwb.batch != null && iwb.batch.getExpiryDate() != null
                            ? iwb.batch.getExpiryDate()
                            : LocalDate.MAX);
        };
        inventoryWithBatches.sort(sorter);

        LocalDate today = LocalDate.now();
        for (InventoryWithBatch iwb : inventoryWithBatches) {
            if (iwb.batch != null && iwb.batch.getExpiryDate() != null) {
                if (iwb.batch.getExpiryDate().isBefore(today)) {
                    log.warn("Found expired batch: {} with expiry date: {}",
                            iwb.batch.getBatchId(), iwb.batch.getExpiryDate());

                }
            }
        }

        List<InventoryAllocation> allocations = new ArrayList<>();
        BigDecimal remaining = requiredQuantity;

        for (InventoryWithBatch iwb : inventoryWithBatches) {
            Inventory inv = iwb.inventory;

            BigDecimal available = inv.getQuantity().subtract(inv.getReservedQuantity());

            if (available.compareTo(BigDecimal.ZERO) > 0) {

                BigDecimal toAllocate = remaining.min(available);

                InventoryAllocation allocation = new InventoryAllocation(
                        inv.getInventoryId(),
                        inv.getProductId(),
                        inv.getBatchId(),
                        inv.getWarehouseId(),
                        inv.getCellId(),
                        toAllocate,
                        iwb.batch != null ? iwb.batch.getExpiryDate() : null
                );
                allocations.add(allocation);

                remaining = remaining.subtract(toAllocate);

                log.debug("Allocated {} from inventory {} (batch: {})",
                        toAllocate, inv.getInventoryId(), inv.getBatchId());

                if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                    break;
                }
            }
        }

        if (remaining.compareTo(BigDecimal.ZERO) > 0) {
            throw AppException.badRequest(
                    String.format("Недостаточно товара на складе. Доступно: %s, требуется: %s",
                            requiredQuantity.subtract(remaining), requiredQuantity)
            );
        }

        log.info("FEFO selection completed. Total allocations: {}", allocations.size());
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
