package by.bsuir.productservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Schema(description = "Запрос на создание поставки (заказа поставщику)")
public record CreateSupplyRequest(
        @Schema(description = "ID поставщика", example = "550e8400-e29b-41d4-a716-446655440020")
        UUID supplierId,

        @Schema(description = "ID склада назначения", example = "550e8400-e29b-41d4-a716-446655440003")
        @NotNull(message = "Склад обязателен")
        UUID warehouseId,

        @Schema(description = "Плановая дата поступления", example = "2026-06-15")
        LocalDate expectedDate,

        @Schema(description = "Примечания к поставке", example = "Срочная поставка молочной продукции")
        String notes,

        @Schema(description = "ID сотрудника, создающего поставку", example = "550e8400-e29b-41d4-a716-446655440005")
        @NotNull(message = "User ID обязателен")
        UUID createdBy,

        @Schema(description = "Позиции поставки")
        List<SupplyItemRequest> items
) {
    @Schema(description = "Позиция поставки")
    public record SupplyItemRequest(
            @Schema(description = "ID товара", example = "550e8400-e29b-41d4-a716-446655440001")
            @NotNull(message = "Product ID обязателен")
            UUID productId,

            @Schema(description = "Ожидаемое количество", example = "500.000")
            @NotNull(message = "Ожидаемое количество обязательно")
            BigDecimal expectedQty,

            @Schema(description = "Закупочная цена за единицу, BYN", example = "2.50")
            BigDecimal unitPrice,

            @Schema(description = "Примечания к позиции", example = "Молоко 3.2%, срок хранения 10 дней")
            String notes
    ) {
    }
}