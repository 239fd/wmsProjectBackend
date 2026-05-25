package by.bsuir.productservice.dto.response;

import by.bsuir.productservice.model.enums.StorageConditions;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record ProductResponse(
        UUID productId,
        String name,
        String sku,
        String barcode,
        String category,
        String description,
        String unitOfMeasure,
        BigDecimal price,
        StorageConditions requiredStorageCondition,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
