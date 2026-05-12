package by.bsuir.productservice.dto.response;

import by.bsuir.productservice.model.enums.StorageConditions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record BatchResponse(
        UUID batchId,
        UUID productId,
        UUID organizationId,
        UUID supplyId,
        String batchNumber,
        LocalDate manufactureDate,
        LocalDate expiryDate,
        String supplier,
        BigDecimal purchasePrice,
        StorageConditions storageConditions,
        LocalDateTime createdAt
) {
}
