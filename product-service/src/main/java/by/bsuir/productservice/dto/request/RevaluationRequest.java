package by.bsuir.productservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Запрос на переоценку товара")
public record RevaluationRequest(
        @Schema(description = "ID товара")
        @NotNull(message = "Товар обязателен")
        UUID productId,

        @Schema(description = "ID склада")
        @NotNull(message = "Склад обязателен")
        UUID warehouseId,

        @Schema(description = "Новая учётная цена за единицу, BYN")
        @NotNull(message = "Новая цена обязательна")
        @Positive(message = "Новая цена должна быть положительной")
        BigDecimal newPrice,

        @Schema(description = "Причина переоценки")
        String reason,

        @Schema(description = "Основание (приказ/документ)")
        String basis,

        @Schema(description = "Ответственное лицо (userId)")
        UUID responsibleUserId,

        @Schema(description = "Комиссия — список userId")
        List<UUID> commissionMembers,

        @Schema(description = "ID бухгалтера, выполняющего переоценку")
        @NotNull(message = "Пользователь обязателен")
        UUID userId,

        @Schema(description = "Примечания")
        String notes
) {
}