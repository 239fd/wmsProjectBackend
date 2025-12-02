package by.bsuir.productservice.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BatchResponse(
        UUID batchId,
        UUID productId,
        String batchNumber,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        String supplier,
        BigDecimal purchasePrice,
        LocalDateTime createdAt
) {
}
