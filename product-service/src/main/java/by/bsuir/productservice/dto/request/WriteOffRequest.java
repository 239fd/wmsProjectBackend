package by.bsuir.productservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(description = "Запрос на списание товара")
public record WriteOffRequest(
        @Schema(description = "ID товара")
        @NotNull(message = "Product ID обязателен")
        UUID productId,

        @Schema(description = "ID склада")
        @NotNull(message = "Warehouse ID обязателен")
        UUID warehouseId,

        @Schema(description = "ID партии")
        UUID batchId,

        @Schema(description = "ID ячейки")
        UUID cellId,

        @Schema(description = "Количество для списания")
        @NotNull(message = "Количество обязательно")
        @Positive(message = "Количество должно быть положительным")
        BigDecimal quantity,

        @Schema(description = "Причина списания: DAMAGE, EXPIRED, SHORTAGE, OTHER")
        @NotNull(message = "Причина списания обязательна")
        String reason,

        @Schema(description = "Основание (документ/приказ)")
        String basis,

        @Schema(description = "Ответственное лицо (userId)")
        UUID responsibleUserId,

        @Schema(description = "Комиссия — список userId")
        List<UUID> commissionMembers,

        @Schema(description = "ID сотрудника, выполняющего списание")
        @NotNull(message = "User ID обязателен")
        UUID userId,

        @Schema(description = "Примечания")
        String notes
) {
}