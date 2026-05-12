package by.bsuir.productservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record StartInventoryRequest(
        @NotNull(message = "Warehouse ID обязателен")
        UUID warehouseId,

        @NotNull(message = "User ID обязателен")
        UUID userId,

        @NotNull(message = "Ответственное лицо обязательно")
        UUID responsibleUserId,

        @NotNull(message = "Причина инвентаризации обязательна")
        String reason,

        List<UUID> commissionMembers,

        String notes
) {
}
