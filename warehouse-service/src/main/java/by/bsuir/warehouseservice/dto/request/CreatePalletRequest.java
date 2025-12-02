package by.bsuir.warehouseservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreatePalletRequest(
        @NotNull(message = "ID стеллажа обязателен")
        UUID rackId,

        @NotNull(message = "Количество паллетомест обязательно")
        @Positive(message = "Количество паллетомест должно быть положительным")
        Integer palletPlaceCount,

        @NotNull(message = "Максимальный вес обязателен")
        @Positive(message = "Максимальный вес должен быть положительным")
        BigDecimal maxWeightKg
) {
}
