package by.bsuir.productservice.dto.request;

import by.bsuir.productservice.model.enums.StorageConditions;

import java.math.BigDecimal;

public record UpdateProductRequest(
        String name,
        String sku,
        String barcode,
        String category,
        String description,
        String unitOfMeasure,
        BigDecimal weightKg,
        BigDecimal volumeM3,
        StorageConditions requiredStorageCondition
) {
}
