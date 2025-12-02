package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ReserveProductRequest(
        @NotNull(message = "Product ID обязателен")
        UUID productId,

        UUID batchId,

        @NotNull(message = "Warehouse ID обязателен")
        UUID warehouseId,

        @NotNull(message = "Количество обязательно")
        @Positive(message = "Количество должно быть положительным")
        BigDecimal quantity,

        String notes
) {
}
