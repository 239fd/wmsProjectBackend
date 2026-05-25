package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record StartInventoryRequest(
        @NotNull(message = "Склад обязателен")
        UUID warehouseId,

        @NotNull(message = "Пользователь обязателен")
        UUID userId,

        @NotNull(message = "Ответственное лицо обязательно")
        UUID responsibleUserId,

        @NotNull(message = "Причина инвентаризации обязательна")
        String reason,

        List<UUID> commissionMembers,

        String notes
) {
}
