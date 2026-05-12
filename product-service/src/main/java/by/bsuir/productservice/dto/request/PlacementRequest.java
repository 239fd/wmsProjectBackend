package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PlacementRequest(
        @NotNull(message = "Batch ID обязателен")
        UUID batchId,

        @NotNull(message = "Warehouse ID обязателен")
        UUID warehouseId,

        @NotNull(message = "Quantity обязателен")
        @Positive(message = "Количество должно быть положительным")
        BigDecimal quantity,

        @NotNull(message = "User ID обязателен")
        UUID userId,

        UUID cellId,

        String notes
) {
}
