package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record PlacementRequest(
        @NotNull(message = "Партия обязательна")
        UUID batchId,

        @NotNull(message = "Склад обязателен")
        UUID warehouseId,

        @NotNull(message = "Количество обязательно")
        @Positive(message = "Количество должно быть положительным")
        BigDecimal quantity,

        @NotNull(message = "Пользователь обязателен")
        UUID userId,

        UUID cellId,

        String notes
) {
}
