package by.bsuir.warehouseservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCellRequest(
        @NotNull(message = "ID стеллажа обязателен")
        UUID rackId,

        @NotNull(message = "Максимальный вес обязателен")
        @Positive(message = "Максимальный вес должен быть положительным")
        BigDecimal maxWeightKg,

        @NotNull(message = "Длина обязательна")
        @Positive(message = "Длина должна быть положительной")
        BigDecimal lengthCm,

        @NotNull(message = "Ширина обязательна")
        @Positive(message = "Ширина должна быть положительной")
        BigDecimal widthCm,

        @NotNull(message = "Высота обязательна")
        @Positive(message = "Высота должна быть положительной")
        BigDecimal heightCm
) {
}
