package by.bsuir.warehouseservice.dto.request;

import by.bsuir.warehouseservice.model.enums.PalletType;
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

        @Positive(message = "Максимальный вес должен быть положительным")
        BigDecimal maxWeightKg,

        @Positive(message = "Максимальная высота слота должна быть положительной")
        BigDecimal maxHeightCm,

        @NotNull(message = "Тип паллета обязателен")
        PalletType palletType
) {
}
