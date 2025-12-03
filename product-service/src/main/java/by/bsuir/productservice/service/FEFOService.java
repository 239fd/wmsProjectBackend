package by.bsuir.productservice.service;

import by.bsuir.productservice.exception.AppException;
import by.bsuir.productservice.model.entity.Inventory;
import by.bsuir.productservice.model.entity.ProductBatch;
import by.bsuir.productservice.repository.InventoryRepository;
import by.bsuir.productservice.repository.ProductBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
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

        log.info("Selecting inventory by FEFO for product {} at warehouse {}, required quantity: {}",
                productId, warehouseId, requiredQuantity);

        List<Inventory> availableInventory = inventoryRepository
                .findByProductIdAndWarehouseId(productId, warehouseId)
                .map(Collections::singletonList)
                .orElse(new ArrayList<>());

        if (availableInventory.isEmpty()) {
            throw AppException.notFound("Товар отсутствует на складе");
        }

        List<InventoryWithBatch> inventoryWithBatches = enrichWithBatchInfo(availableInventory);
        inventoryWithBatches.sort(Comparator.comparing(iwb ->
                iwb.batch != null && iwb.batch.getExpiryDate() != null
                    ? iwb.batch.getExpiryDate()
                    : LocalDate.MAX
        ));

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

    private static class InventoryWithBatch {
        final Inventory inventory;
        final ProductBatch batch;

        InventoryWithBatch(Inventory inventory, ProductBatch batch) {
            this.inventory = inventory;
            this.batch = batch;
        }
    }

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

        public UUID getInventoryId() { return inventoryId; }
        public UUID getProductId() { return productId; }
        public UUID getBatchId() { return batchId; }
        public UUID getWarehouseId() { return warehouseId; }
        public UUID getCellId() { return cellId; }
        public BigDecimal getQuantity() { return quantity; }
        public LocalDate getExpiryDate() { return expiryDate; }

        @Override
        public String toString() {
            return String.format("Allocation[inventory=%s, batch=%s, quantity=%s, expiry=%s]",
                    inventoryId, batchId, quantity, expiryDate);
        }
    }
}
