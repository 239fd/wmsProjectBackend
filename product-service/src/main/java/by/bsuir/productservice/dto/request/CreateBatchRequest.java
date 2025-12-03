package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateBatchRequest(
        @NotNull(message = "Product ID обязателен")
        UUID productId,

        String batchNumber,

        LocalDate manufactureDate,

        LocalDate expiryDate,

        String supplier,

        @Positive(message = "Закупочная цена должна быть положительной")
        BigDecimal purchasePrice
) {
}
