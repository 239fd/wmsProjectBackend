package by.bsuir.productservice.dto.response;

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
        BigDecimal weightKg,
        BigDecimal volumeM3,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
