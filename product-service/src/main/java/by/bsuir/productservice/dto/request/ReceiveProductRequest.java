package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record ReceiveProductRequest(
        @NotNull(message = "Товар обязателен")
        UUID productId,

        UUID batchId,

        @NotNull(message = "Склад обязателен")
        UUID warehouseId,

        UUID cellId,

        @NotNull(message = "Количество обязательно")
        @Positive(message = "Количество должно быть положительным")
        BigDecimal quantity,

        @NotNull(message = "Пользователь обязателен")
        UUID userId,

        UUID supplyId,

        String notes
) {
}
