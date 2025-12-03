package by.bsuir.productservice.dto.response;

import by.bsuir.productservice.model.enums.InventoryStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record InventoryResponse(
        UUID inventoryId,
        UUID productId,
        UUID batchId,
        UUID warehouseId,
        UUID cellId,
        BigDecimal quantity,
        BigDecimal reservedQuantity,
        BigDecimal availableQuantity,
        InventoryStatus status,
        LocalDateTime lastUpdated
) {
    public InventoryResponse {

        if (availableQuantity == null && quantity != null && reservedQuantity != null) {
            availableQuantity = quantity.subtract(reservedQuantity);
        }
    }
}
